package lynks.auth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.util.DefaultResourceRetriever
import com.nimbusds.oauth2.sdk.*
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic
import com.nimbusds.oauth2.sdk.auth.Secret
import com.nimbusds.oauth2.sdk.http.HTTPRequest
import com.nimbusds.oauth2.sdk.id.ClientID
import com.nimbusds.oauth2.sdk.id.Issuer
import com.nimbusds.oauth2.sdk.id.State
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import com.nimbusds.openid.connect.sdk.*
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import lynks.common.Environment
import lynks.common.UserId
import lynks.user.OIDC_SUBJECT_MAX_LENGTH
import lynks.user.UserService
import lynks.util.loggerFor
import java.net.URI
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant

enum class SsoFailure(val code: String) {
    EXPIRED("expired"),
    DENIED("denied"),
    FAILED("failed"),
    UNLINKED("unlinked"),
    UNAVAILABLE("unavailable")
}

sealed interface SsoOutcome {
    data class SignedIn(val userId: UserId, val returnTo: String) : SsoOutcome
    data class Failed(val failure: SsoFailure) : SsoOutcome
}

data class SsoStart(val login: PendingLogin, val authorizationUrl: String)

class OidcUnavailableException(cause: Throwable) : RuntimeException("Single sign-on provider unavailable", cause)

class OidcService(
    private val config: Environment.Oidc,
    private val userService: UserService,
    private val timeoutMillis: Int = 5000,
    private val clock: Clock = Clock.systemUTC()
) {

    private val log = loggerFor<OidcService>()

    val enabled: Boolean get() = config.enabled

    private class Provider(val metadata: OIDCProviderMetadata, val validator: IDTokenValidator)

    private data class Identity(val subject: String, val accessToken: BearerAccessToken)

    private val providerLock = Mutex()

    @Volatile
    private var provider: Provider? = null

    // without it every request queues behind the lock for a full timeout while the provider is down
    private var discoveryFailedAt: Instant? = null

    private val clientId by lazy { ClientID(config.clientId) }
    private val redirectUri by lazy { URI(config.redirectUri!!) }

    suspend fun start(returnTo: String): SsoStart {
        val provider = provider()
        val state = State()
        val nonce = Nonce()
        val verifier = CodeVerifier()
        val request = AuthenticationRequest.Builder(
            ResponseType.CODE,
            Scope(OIDCScopeValue.OPENID, OIDCScopeValue.PROFILE),
            clientId,
            redirectUri
        )
            .endpointURI(provider.metadata.authorizationEndpointURI)
            .state(state)
            .nonce(nonce)
            .codeChallenge(verifier, CodeChallengeMethod.S256)
            .build()
        val login = PendingLogin(state.value, nonce.value, verifier.value, returnTo)
        return SsoStart(login, request.toURI().toString())
    }

    suspend fun complete(state: String?, pending: PendingLogin?, code: String?, error: String?): SsoOutcome {
        fun failed(failure: SsoFailure) = SsoOutcome.Failed(failure)
        // the cookie ties the callback to the browser that started it, so nobody can hand a victim their own login
        if (state == null || pending == null || !MessageDigest.isEqual(state.toByteArray(), pending.state.toByteArray())) {
            return failed(SsoFailure.EXPIRED)
        }

        if (error != null) {
            log.info("Single sign-on provider returned error {}", error.filter { it.isLetterOrDigit() || it == '_' }.take(64))
            return failed(if (error == "access_denied") SsoFailure.DENIED else SsoFailure.FAILED)
        }
        if (code.isNullOrEmpty()) return failed(SsoFailure.FAILED)

        val provider = try {
            provider()
        } catch (_: OidcUnavailableException) {
            return failed(SsoFailure.UNAVAILABLE)
        }
        val identity = try {
            withContext(Dispatchers.IO) { exchange(provider, code, pending) }
        } catch (e: Exception) {
            log.warn("Single sign-on token exchange or validation failed: {}", e.message)
            return failed(SsoFailure.FAILED)
        }

        // the callback is a browser navigation, so even a database failure has to end in a redirect rather than a 500
        return try {
            signIn(provider, identity, pending.returnTo)
        } catch (e: Exception) {
            log.error("Resolving a single sign-on subject failed", e)
            failed(SsoFailure.FAILED)
        }
    }

    private suspend fun signIn(provider: Provider, identity: Identity, returnTo: String): SsoOutcome {
        val owner = userService.findBySubject(identity.subject)
        if (owner != null) {
            return if (owner.activated) SsoOutcome.SignedIn(owner.id, returnTo)
            else SsoOutcome.Failed(SsoFailure.DENIED)
        }
        // the first sign in matches the provider's username exactly, and only the subject counts after that
        val username = withContext(Dispatchers.IO) { preferredUsername(provider, identity) }
        val linked = username?.let { userService.linkSubjectByUsername(it, identity.subject) }
        if (linked == null) {
            log.info("Single sign-on subject matches no user")
            return SsoOutcome.Failed(SsoFailure.UNLINKED)
        }
        log.info("Linked user {} to a single sign-on subject by username '{}'", linked, username)
        return SsoOutcome.SignedIn(linked, returnTo)
    }

    private fun exchange(provider: Provider, code: String, pending: PendingLogin): Identity {
        val grant = AuthorizationCodeGrant(AuthorizationCode(code), redirectUri, CodeVerifier(pending.codeVerifier))
        val clientAuth = ClientSecretBasic(clientId, Secret(config.clientSecret))
        val request = TokenRequest.Builder(provider.metadata.tokenEndpointURI, clientAuth, grant).build()
            .toHTTPRequest()
            .withTimeouts()
        val response = OIDCTokenResponseParser.parse(request.send())
        if (!response.indicatesSuccess()) {
            throw IllegalStateException("token endpoint returned ${response.toErrorResponse().errorObject.code}")
        }
        val tokens = (response.toSuccessResponse() as OIDCTokenResponse).oidcTokens
        val claims = provider.validator.validate(tokens.idToken, Nonce(pending.nonce))
        val subject = claims.subject.value
        check(subject.length <= OIDC_SUBJECT_MAX_LENGTH) { "subject is longer than $OIDC_SUBJECT_MAX_LENGTH characters" }
        return Identity(subject, tokens.bearerAccessToken)
    }

    // from userinfo rather than the id token, which Authelia keeps to a minimum by default
    private fun preferredUsername(provider: Provider, identity: Identity): String? {
        val endpoint = provider.metadata.userInfoEndpointURI ?: return null
        return try {
            val request = UserInfoRequest(endpoint, identity.accessToken).toHTTPRequest().withTimeouts()
            val response = UserInfoResponse.parse(request.send())
            if (!response.indicatesSuccess()) {
                log.warn("Single sign-on userinfo returned {}", response.toErrorResponse().errorObject.code)
                return null
            }
            val info = response.toSuccessResponse().userInfo ?: return null
            // a userinfo response for anyone but the token's subject must be ignored
            if (info.subject.value != identity.subject) {
                log.warn("Single sign-on userinfo subject does not match the id token")
                return null
            }
            info.preferredUsername
        } catch (e: Exception) {
            log.warn("Single sign-on userinfo request failed: {}", e.message)
            null
        }
    }

    // fetched on first use rather than at startup, so password login works while the provider is down
    private suspend fun provider(): Provider {
        provider?.let { return it }
        return providerLock.withLock {
            provider?.let { return@withLock it }
            val failedAt = discoveryFailedAt
            if (failedAt != null && Duration.between(failedAt, clock.instant()) < DISCOVERY_RETRY) {
                throw OidcUnavailableException(IllegalStateException("provider discovery failed recently"))
            }
            try {
                withContext(Dispatchers.IO) { discover() }.also {
                    provider = it
                    discoveryFailedAt = null
                }
            } catch (e: Exception) {
                log.warn("Single sign-on provider discovery failed: {}", e.message)
                discoveryFailedAt = clock.instant()
                throw OidcUnavailableException(e)
            }
        }
    }

    private fun discover(): Provider {
        val metadata = OIDCProviderMetadata.resolve(Issuer(config.issuer), timeoutMillis, timeoutMillis)
        val algorithms = metadata.idTokenJWSAlgs.orEmpty().filter { it in JWSAlgorithm.Family.SIGNATURE }.toSet()
        require(algorithms.isNotEmpty()) { "provider offers no asymmetric id token signing algorithm" }
        val jwkSource = JWKSourceBuilder.create<SecurityContext>(
            metadata.jwkSetURI.toURL(),
            DefaultResourceRetriever(timeoutMillis, timeoutMillis)
        ).build()
        val validator = IDTokenValidator(metadata.issuer, clientId, JWSVerificationKeySelector(algorithms, jwkSource), null)
        log.info("Single sign-on provider {} discovered", metadata.issuer)
        return Provider(metadata, validator)
    }

    private fun HTTPRequest.withTimeouts() = apply {
        connectTimeout = timeoutMillis
        readTimeout = timeoutMillis
    }

    private companion object {
        val DISCOVERY_RETRY: Duration = Duration.ofSeconds(30)
    }
}
