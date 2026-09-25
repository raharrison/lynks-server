package lynks.endpoint

import com.fasterxml.jackson.databind.JsonNode
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import lynks.auth.*
import lynks.common.Environment
import lynks.common.ServerTest
import lynks.installPlugins
import lynks.user.*
import lynks.util.DUMMY_USER_PASSWORD
import lynks.util.JsonMapper
import lynks.util.activateUser
import lynks.util.createDummyUser
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class AuthEndpointTest : ServerTest() {

    private val provider = PROVIDER

    @BeforeEach
    fun startProvider() {
        provider.start()
    }

    private val user2 = lynks.common.UserId("user2-id")
    private val defaultUser = Environment.auth.defaultUserName

    private fun authConfig(
        passwordLogin: Boolean = true,
        oidcEnabled: Boolean = true
    ) = Environment.auth.copy(
        enabled = true,
        passwordLoginEnabled = passwordLogin,
        oidc = Environment.Oidc(
            enabled = oidcEnabled,
            issuer = provider.issuer,
            clientId = CLIENT_ID,
            clientSecret = CLIENT_SECRET,
            redirectUri = REDIRECT_URI,
            label = "Sign in with Test"
        )
    )

    private fun lynks(config: Environment.Auth = authConfig(), test: suspend Browser.() -> Unit) = testApplication {
        application {
            installPlugins()
            val userService = UserService(TwoFactorService())
            val sessionService = SessionService(config.session)
            val cookies = AuthCookies(false, sessionService.maxAge)
            val oidcService = OidcService(config.oidc, userService)
            installAuth(config, userService, sessionService, cookies)
            routing {
                route(Environment.server.rootPath) {
                    authUnprotected(config, userService, sessionService, oidcService, cookies)
                    authenticate(AUTH_PROVIDER) {
                        userProtected(userService, sessionService)
                        authProtected(sessionService)
                    }
                }
            }
        }
        createDummyUser("user2", id = user2)
        Browser(createClient { followRedirects = false }, provider).test()
    }

    private fun sessionCount(): Long = transaction { UserSessions.selectAll().count() }

    @Test
    fun testUnauthenticatedIsRejected() = lynks {
        assertThat(get("/api/user").status).isEqualTo(HttpStatusCode.Unauthorized)
        cookies["lynks_session"] = "made-up"
        assertThat(get("/api/user").status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun testPasswordLoginSessionAndLogout() = lynks {
        assertThat(login(defaultUser, DUMMY_USER_PASSWORD).status).isEqualTo(HttpStatusCode.OK)
        assertThat(cookies).containsKey("lynks_session")
        val user = json(get("/api/user"))
        assertThat(user["username"].textValue()).isEqualTo(defaultUser)

        val sessions = json(get("/api/user/sessions"))
        assertThat(sessions).hasSize(1)
        assertThat(sessions[0]["method"].textValue()).isEqualTo("password")
        assertThat(sessions[0]["current"].booleanValue()).isTrue()

        assertThat(post("/api/logout").status).isEqualTo(HttpStatusCode.OK)
        assertThat(cookies).doesNotContainKey("lynks_session")
        assertThat(sessionCount()).isZero()
        assertThat(get("/api/user").status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun testSessionCookieAttributes() = lynks {
        val cookie = login(defaultUser, DUMMY_USER_PASSWORD).setCookie().single { it.name == "lynks_session" }
        assertThat(cookie.httpOnly).isTrue()
        assertThat(cookie.path).isEqualTo("/")
        assertThat(cookie.extensions["SameSite"]).isEqualTo("Lax")
        assertThat(cookie.maxAge).isEqualTo(90 * 24 * 60 * 60)
    }

    @Test
    fun testLoginReplacesThePreviousSession() = lynks {
        login("user2", DUMMY_USER_PASSWORD)
        val previous = cookies.getValue("lynks_session")
        login(defaultUser, DUMMY_USER_PASSWORD)
        assertThat(cookies.getValue("lynks_session")).isNotEqualTo(previous)
        assertThat(sessionCount()).isEqualTo(1)
        assertThat(json(get("/api/user"))["username"].textValue()).isEqualTo(defaultUser)
    }

    @Test
    fun testFailedLoginCreatesNoSession() = lynks {
        assertThat(login(defaultUser, "wrong-password").status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(cookies).doesNotContainKey("lynks_session")
        assertThat(sessionCount()).isZero()
    }

    @Test
    fun testDeactivationEndsSessions() = lynks {
        login("user2", DUMMY_USER_PASSWORD)
        assertThat(get("/api/user").status).isEqualTo(HttpStatusCode.OK)
        activateUser("user2", false)
        assertThat(get("/api/user").status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun testRevokeSessions() = lynks {
        val other = Browser(client, provider)
        other.login(defaultUser, DUMMY_USER_PASSWORD)
        val theirs = Browser(client, provider)
        theirs.login("user2", DUMMY_USER_PASSWORD)
        login(defaultUser, DUMMY_USER_PASSWORD)

        val sessions = json(get("/api/user/sessions"))
        assertThat(sessions).hasSize(2)
        val theirId = json(theirs.get("/api/user/sessions"))[0]["id"].textValue()
        assertThat(delete("/api/user/sessions/$theirId").status).isEqualTo(HttpStatusCode.NotFound)
        assertThat(theirs.get("/api/user").status).isEqualTo(HttpStatusCode.OK)

        assertThat(delete("/api/user/sessions").status).isEqualTo(HttpStatusCode.OK)
        assertThat(get("/api/user").status).isEqualTo(HttpStatusCode.OK)
        assertThat(other.get("/api/user").status).isEqualTo(HttpStatusCode.Unauthorized)

        val currentId = json(get("/api/user/sessions")).single()["id"].textValue()
        assertThat(delete("/api/user/sessions/$currentId").status).isEqualTo(HttpStatusCode.OK)
        assertThat(get("/api/user").status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun testChangePasswordRevokesOtherSessions() = lynks {
        val other = Browser(client, provider)
        other.login(defaultUser, DUMMY_USER_PASSWORD)
        login(defaultUser, DUMMY_USER_PASSWORD)
        val response = post("/api/user/changePassword", ChangePasswordRequest(DUMMY_USER_PASSWORD, "password456"))
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(get("/api/user").status).isEqualTo(HttpStatusCode.OK)
        assertThat(other.get("/api/user").status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun testCrossSiteWriteIsRefused() = lynks {
        login(defaultUser, DUMMY_USER_PASSWORD)
        val response = post("/api/logout") { header("Sec-Fetch-Site", "same-site") }
        assertThat(response.status).isEqualTo(HttpStatusCode.Forbidden)
        assertThat(get("/api/user").status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun testAuthConfig() = lynks {
        val config = json(get("/api/auth/config"))
        assertThat(config["passwordLogin"].booleanValue()).isTrue()
        assertThat(config["sso"]["label"].textValue()).isEqualTo("Sign in with Test")
    }

    @Test
    fun testAuthConfigWithoutSso() = lynks(authConfig(oidcEnabled = false)) {
        assertThat(json(get("/api/auth/config"))["sso"].isNull).isTrue()
        assertThat(get("/api/auth/oidc/login").status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun testPasswordLoginDisabled() = lynks(authConfig(passwordLogin = false)) {
        assertThat(json(get("/api/auth/config"))["passwordLogin"].booleanValue()).isFalse()
        assertThat(login(defaultUser, DUMMY_USER_PASSWORD).status).isEqualTo(HttpStatusCode.Forbidden)
        assertThat(sessionCount()).isZero()
    }

    @Test
    fun testSsoSignIn() = lynks {
        linkSubject(defaultUser, "sub-1")
        val start = get("/api/auth/oidc/login?returnTo=/entries/abc")
        assertThat(start.status).isEqualTo(HttpStatusCode.Found)
        val authorize = Url(start.headers[HttpHeaders.Location]!!)
        assertThat(authorize.toString()).startsWith("${provider.issuer}/authorize")
        assertThat(authorize.parameters["response_type"]).isEqualTo("code")
        assertThat(authorize.parameters["client_id"]).isEqualTo(CLIENT_ID)
        assertThat(authorize.parameters["redirect_uri"]).isEqualTo(REDIRECT_URI)
        assertThat(authorize.parameters["scope"]!!.split(" ")).containsExactlyInAnyOrder("openid", "profile")
        assertThat(authorize.parameters["code_challenge_method"]).isEqualTo("S256")
        assertThat(authorize.parameters["code_challenge"]).isNotBlank()
        assertThat(authorize.parameters["nonce"]).isNotBlank()
        assertThat(PendingLogin.decode(cookies.getValue("lynks_oidc"))?.state).isEqualTo(authorize.parameters["state"])

        val callback = callback(authorize, provider.idToken("sub-1", authorize.parameters["nonce"]))
        assertThat(callback.status).isEqualTo(HttpStatusCode.Found)
        assertThat(callback.headers[HttpHeaders.Location]).isEqualTo("/entries/abc")
        assertThat(cookies).doesNotContainKey("lynks_oidc")
        assertThat(json(get("/api/user"))["username"].textValue()).isEqualTo(defaultUser)
        assertThat(json(get("/api/user/sessions"))[0]["method"].textValue()).isEqualTo("oidc")

        val tokenRequest = provider.tokenRequests().single()
        assertThat(tokenRequest.bodyAsString).contains("code=code-1", "code_verifier=")
        val basic = Base64.getEncoder().encodeToString("$CLIENT_ID:$CLIENT_SECRET".toByteArray())
        assertThat(tokenRequest.getHeader(HttpHeaders.Authorization)).isEqualTo("Basic $basic")
    }

    @Test
    fun testSsoReturnToIsSanitised() = lynks {
        linkSubject(defaultUser, "sub-1")
        val authorize = startLogin("//evil.example/steal")
        val callback = callback(authorize, provider.idToken("sub-1", authorize.parameters["nonce"]))
        assertThat(callback.headers[HttpHeaders.Location]).isEqualTo("/")
    }

    @Test
    fun testSsoUnlinkedSubject() = lynks {
        provider.stubUserInfo("sub-unknown", "nobody")
        val authorize = startLogin()
        val callback = callback(authorize, provider.idToken("sub-unknown", authorize.parameters["nonce"]))
        assertThat(callback.headers[HttpHeaders.Location]).isEqualTo("/login?sso=unlinked")
        assertThat(cookies).doesNotContainKey("lynks_session")
        assertThat(sessionCount()).isZero()
    }

    @Test
    fun testSsoDeactivatedUser() = lynks {
        linkSubject("user2", "sub-2")
        activateUser("user2", false)
        val authorize = startLogin()
        val callback = callback(authorize, provider.idToken("sub-2", authorize.parameters["nonce"]))
        assertThat(callback.headers[HttpHeaders.Location]).isEqualTo("/login?sso=denied")
        assertThat(sessionCount()).isZero()
    }

    @Test
    fun testSsoProviderDenied() = lynks {
        val authorize = startLogin()
        val state = authorize.parameters["state"]
        val callback = get("/api/auth/oidc/callback?error=access_denied&state=$state")
        assertThat(callback.headers[HttpHeaders.Location]).isEqualTo("/login?sso=denied")
        assertThat(provider.tokenRequests()).isEmpty()
    }

    @Test
    fun testSsoRejectsBadTokens() = lynks {
        linkSubject(defaultUser, "sub-1")
        val otherKey = RSAKeyGenerator(2048).keyID(provider.key.keyID).generate()
        val badTokens = listOf<(String?) -> String>(
            { nonce -> provider.idToken("sub-1", "not-$nonce") },
            { _ -> provider.idToken("sub-1", null) },
            { nonce -> provider.idToken("sub-1", nonce, audience = "someone-else") },
            { nonce -> provider.idToken("sub-1", nonce, issuer = "https://evil.example") },
            { nonce -> provider.idToken("sub-1", nonce, expires = Date(System.currentTimeMillis() - 600_000)) },
            { nonce -> provider.idToken("sub-1", nonce, key = otherKey) }
        )
        badTokens.forEach { token ->
            val authorize = startLogin()
            val callback = callback(authorize, token(authorize.parameters["nonce"]))
            assertThat(callback.headers[HttpHeaders.Location]).isEqualTo("/login?sso=failed")
        }
        assertThat(sessionCount()).isZero()
    }

    @Test
    fun testSsoTokenEndpointError() = lynks {
        val authorize = startLogin()
        provider.stubTokenError()
        val callback = get("/api/auth/oidc/callback?code=code-1&state=${authorize.parameters["state"]}")
        assertThat(callback.headers[HttpHeaders.Location]).isEqualTo("/login?sso=failed")
    }

    @Test
    fun testSsoStateMustMatchCookie() = lynks {
        linkSubject(defaultUser, "sub-1")
        val authorize = startLogin()
        val token = provider.idToken("sub-1", authorize.parameters["nonce"])

        cookies.remove("lynks_oidc")
        assertThat(callback(authorize, token).headers[HttpHeaders.Location]).isEqualTo("/login?sso=expired")
        cookies["lynks_oidc"] = "not-a-pending-login"
        assertThat(callback(authorize, token).headers[HttpHeaders.Location]).isEqualTo("/login?sso=expired")

        // the login started in another browser, so this one's cookie holds a different state
        val victim = Browser(client, provider)
        victim.startLogin()
        assertThat(victim.callback(authorize, token).headers[HttpHeaders.Location]).isEqualTo("/login?sso=expired")
        assertThat(sessionCount()).isZero()
    }

    @Test
    fun testSsoStateIsSingleUse() = lynks {
        linkSubject(defaultUser, "sub-1")
        val authorize = startLogin()
        val token = provider.idToken("sub-1", authorize.parameters["nonce"])
        assertThat(callback(authorize, token).headers[HttpHeaders.Location]).isEqualTo("/")
        assertThat(cookies).doesNotContainKey("lynks_oidc")
        assertThat(callback(authorize, token).headers[HttpHeaders.Location]).isEqualTo("/login?sso=expired")
    }

    @Test
    fun testSsoProviderUnavailable() = lynks {
        provider.stop()
        val start = get("/api/auth/oidc/login")
        assertThat(start.headers[HttpHeaders.Location]).isEqualTo("/login?sso=unavailable")
        assertThat(cookies).doesNotContainKey("lynks_oidc")
        assertThat(login(defaultUser, DUMMY_USER_PASSWORD).status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun testFirstSignInLinksByUsername() = lynks {
        provider.stubUserInfo("sub-2", "user2")
        val authorize = startLogin()
        val callback = callback(authorize, provider.idToken("sub-2", authorize.parameters["nonce"]))
        assertThat(callback.headers[HttpHeaders.Location]).isEqualTo("/")
        assertThat(json(get("/api/user"))["username"].textValue()).isEqualTo("user2")
        assertThat(provider.userInfoRequests().single().getHeader(HttpHeaders.Authorization)).isEqualTo("Bearer access-1")

        // once linked only the subject counts, so a renamed provider user still signs in as the same user
        provider.stubUserInfo("sub-2", "renamed")
        val again = startLogin()
        callback(again, provider.idToken("sub-2", again.parameters["nonce"]))
        assertThat(json(get("/api/user"))["username"].textValue()).isEqualTo("user2")
        assertThat(provider.userInfoRequests()).hasSize(1)
    }

    @Test
    fun testLinkByUsernameIgnoresMismatchedUserInfo() = lynks {
        provider.stubUserInfo("someone-else", "user2")
        val authorize = startLogin()
        val callback = callback(authorize, provider.idToken("sub-2", authorize.parameters["nonce"]))
        assertThat(callback.headers[HttpHeaders.Location]).isEqualTo("/login?sso=unlinked")
    }

    @Test
    fun testLinkByUsernameNeverReplacesALink() = lynks {
        linkSubject("user2", "sub-2")
        provider.stubUserInfo("sub-other", "user2")
        val authorize = startLogin()
        val callback = callback(authorize, provider.idToken("sub-other", authorize.parameters["nonce"]))
        assertThat(callback.headers[HttpHeaders.Location]).isEqualTo("/login?sso=unlinked")
    }

    private fun linkSubject(username: String, subject: String) {
        assertThat(UserService(TwoFactorService()).linkSubjectByUsername(username, subject)).isNotNull()
    }

    private class Browser(val client: HttpClient, private val provider: FakeProvider) {

        val cookies = mutableMapOf<String, String>()

        suspend fun get(path: String, block: HttpRequestBuilder.() -> Unit = {}) = send(HttpMethod.Get, path, null, block)

        suspend fun post(path: String, body: Any? = null, block: HttpRequestBuilder.() -> Unit = {}) =
            send(HttpMethod.Post, path, body, block)

        suspend fun delete(path: String) = send(HttpMethod.Delete, path, null) {}

        suspend fun login(username: String, password: String) = post("/api/login", AuthRequest(username, password))

        suspend fun startLogin(returnTo: String? = null): Url {
            val response = get("/api/auth/oidc/login" + (returnTo?.let { "?returnTo=${it.encodeURLParameter()}" } ?: ""))
            assertThat(response.status).isEqualTo(HttpStatusCode.Found)
            return Url(response.headers[HttpHeaders.Location]!!)
        }

        suspend fun callback(authorize: Url, idToken: String): HttpResponse {
            provider.stubToken(idToken)
            return get("/api/auth/oidc/callback?code=code-1&state=${authorize.parameters["state"]}")
        }

        suspend fun json(response: HttpResponse): JsonNode = JsonMapper.defaultMapper.readTree(response.bodyAsText())

        private suspend fun send(
            method: HttpMethod,
            path: String,
            body: Any?,
            block: HttpRequestBuilder.() -> Unit
        ): HttpResponse {
            val response = client.request(path) {
                this.method = method
                if (cookies.isNotEmpty()) {
                    header(HttpHeaders.Cookie, cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
                }
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(JsonMapper.defaultMapper.writeValueAsString(body))
                }
                block()
            }
            response.setCookie().forEach {
                if (it.maxAge == 0) cookies.remove(it.name) else cookies[it.name] = it.value
            }
            return response
        }
    }

    private class FakeProvider(port: Int) {

        val issuer = "http://localhost:$port"
        val key: RSAKey = SIGNING_KEY
        private val server = WireMockServer(WireMockConfiguration.options().port(port))

        fun start() {
            if (!server.isRunning) server.start()
            server.resetAll()
            server.stubFor(
                get(urlEqualTo("/.well-known/openid-configuration")).willReturn(
                    okJson(
                        """
                        {
                          "issuer": "$issuer",
                          "authorization_endpoint": "$issuer/authorize",
                          "token_endpoint": "$issuer/token",
                          "userinfo_endpoint": "$issuer/userinfo",
                          "jwks_uri": "$issuer/jwks",
                          "response_types_supported": ["code"],
                          "subject_types_supported": ["public"],
                          "id_token_signing_alg_values_supported": ["RS256"],
                          "code_challenge_methods_supported": ["S256"]
                        }
                        """.trimIndent()
                    )
                )
            )
            server.stubFor(get(urlEqualTo("/jwks")).willReturn(okJson(JWKSet(key.toPublicJWK()).toString())))
        }

        fun stop() {
            if (server.isRunning) server.stop()
        }

        fun stubToken(idToken: String) {
            server.stubFor(
                post(urlEqualTo("/token")).willReturn(
                    okJson("""{"access_token":"access-1","token_type":"Bearer","expires_in":300,"id_token":"$idToken"}""")
                )
            )
        }

        fun stubTokenError() {
            server.stubFor(
                post(urlEqualTo("/token")).willReturn(
                    aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                        .withBody("""{"error":"invalid_grant"}""")
                )
            )
        }

        fun stubUserInfo(subject: String, username: String) {
            server.stubFor(
                get(urlEqualTo("/userinfo")).willReturn(okJson("""{"sub":"$subject","preferred_username":"$username"}"""))
            )
        }

        fun tokenRequests() = server.findAll(postRequestedFor(urlEqualTo("/token")))

        fun userInfoRequests() = server.findAll(getRequestedFor(urlEqualTo("/userinfo")))

        fun idToken(
            subject: String,
            nonce: String?,
            issuer: String = this.issuer,
            audience: String = CLIENT_ID,
            expires: Date = Date(System.currentTimeMillis() + 300_000),
            key: RSAKey = this.key
        ): String {
            val claims = JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject)
                .audience(audience)
                .issueTime(Date())
                .expirationTime(expires)
                .apply { if (nonce != null) claim("nonce", nonce) }
                .build()
            val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(), claims)
            jwt.sign(RSASSASigner(key))
            return jwt.serialize()
        }
    }

    companion object {
        private const val PROVIDER_PORT = 3894
        private const val CLIENT_ID = "lynks"
        private const val CLIENT_SECRET = "client-secret"
        private const val REDIRECT_URI = "http://localhost/api/auth/oidc/callback"
        private val SIGNING_KEY: RSAKey = RSAKeyGenerator(2048).keyID("k1").generate()

        // one server for the class, since restarting it before every test occasionally left discovery past its timeout
        private val PROVIDER = FakeProvider(PROVIDER_PORT)

        @AfterAll
        @JvmStatic
        fun stopProvider() = PROVIDER.stop()
    }
}
