package lynks.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import lynks.common.Environment
import lynks.common.ErrorResponse
import lynks.user.UserService

const val AUTH_PROVIDER = "auth_session"

fun Application.installAuth(
    config: Environment.Auth,
    userService: UserService,
    sessionService: SessionService,
    cookies: AuthCookies,
    rootPath: String
) {
    config.validate()
    install(AppCors)
    install(CrossSiteGuard) {
        sessionCookies = cookies
        exemptPaths = APP_SIGN_IN_PATHS.map { rootPath + it }.toSet()
    }
    install(Authentication) {
        provider(AUTH_PROVIDER) {
            authenticate { context ->
                val principal = if (config.enabled) {
                    context.call.sessionToken(cookies)?.let { sessionService.authenticate(it) }
                } else {
                    userService.getPrincipal(config.defaultUserName)
                }
                if (principal != null) {
                    context.principal(principal)
                } else {
                    context.challenge(AUTH_PROVIDER, AuthenticationFailedCause.NoCredentials) { challenge, call ->
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
                        challenge.complete()
                    }
                }
            }
        }
    }
}

// A request carrying a bearer token is authenticated by it alone, so a cookie riding along is never what lets it in
fun ApplicationCall.sessionToken(cookies: AuthCookies): String? = bearerToken() ?: cookies.sessionToken(this)

fun ApplicationCall.bearerToken(): String? {
    val header = request.headers[HttpHeaders.Authorization] ?: return null
    if (!header.startsWith(BEARER_PREFIX, ignoreCase = true)) return null
    return header.substring(BEARER_PREFIX.length).trim().ifEmpty { null }
}

private const val BEARER_PREFIX = "Bearer "

private val UNSAFE_METHODS = setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete)

class CrossSiteGuardConfig {
    var sessionCookies: AuthCookies? = null

    // endpoints that neither read nor set a cookie, so a cross-site caller can only act as whoever it proves to be
    var exemptPaths: Set<String> = emptySet()
}

// SameSite=Lax treats every subdomain of the same domain as the same site, so a compromised
// sibling app could otherwise send credentialed writes. Browsers always send Sec-Fetch-Site.
// The app's WebView is always cross-site, but it sends a bearer token, which no other site holds, and never the
// session cookie. A forged request carries the cookie, so a bearer header beside one is still checked: /login sets it.
val CrossSiteGuard = createApplicationPlugin("CrossSiteGuard", ::CrossSiteGuardConfig) {
    val cookies = pluginConfig.sessionCookies
    val exemptPaths = pluginConfig.exemptPaths
    onCall { call ->
        if (call.request.httpMethod !in UNSAFE_METHODS) return@onCall
        if (call.request.path() in exemptPaths) return@onCall
        if (call.bearerToken() != null && cookies?.sessionToken(call) == null) return@onCall
        val site = call.request.headers["Sec-Fetch-Site"] ?: return@onCall
        if (site != "same-origin") {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("Cross-site request refused"))
        }
    }
}
