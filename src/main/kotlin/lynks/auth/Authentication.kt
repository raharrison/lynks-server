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
    cookies: AuthCookies
) {
    config.validate()
    install(CrossSiteGuard)
    install(Authentication) {
        provider(AUTH_PROVIDER) {
            authenticate { context ->
                val principal = if (config.enabled) {
                    cookies.sessionToken(context.call)?.let { sessionService.authenticate(it) }
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

private val UNSAFE_METHODS = setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete)

// SameSite=Lax treats every subdomain of the same domain as the same site, so a compromised
// sibling app could otherwise send credentialed writes. Browsers always send Sec-Fetch-Site.
val CrossSiteGuard = createApplicationPlugin("CrossSiteGuard") {
    onCall { call ->
        if (call.request.httpMethod !in UNSAFE_METHODS) return@onCall
        val site = call.request.headers["Sec-Fetch-Site"] ?: return@onCall
        if (site != "same-origin") {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("Cross-site request refused"))
        }
    }
}
