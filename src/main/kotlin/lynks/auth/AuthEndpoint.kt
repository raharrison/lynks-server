package lynks.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import lynks.common.Environment
import lynks.common.ErrorResponse
import lynks.common.UserId
import lynks.common.exception.NotFoundException
import lynks.user.AuthRequest
import lynks.user.AuthResult
import lynks.user.UserPrincipal
import lynks.user.UserService
import lynks.util.userId

data class AuthConfigResponse(val passwordLogin: Boolean, val sso: SsoConfig?)
data class SsoConfig(val label: String)

private const val LOGIN_PAGE = "/login"

fun Route.authUnprotected(
    config: Environment.Auth,
    userService: UserService,
    sessionService: SessionService,
    oidcService: OidcService,
    cookies: AuthCookies
) {

    get("/auth/config") {
        val sso = if (config.oidc.enabled) SsoConfig(config.oidc.label) else null
        call.respond(AuthConfigResponse(config.passwordLoginEnabled, sso))
    }

    post("/login") {
        if (!config.passwordLoginEnabled) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("Password sign in is disabled"))
            return@post
        }
        val request = call.receive<AuthRequest>()
        val outcome = userService.checkAuth(request, twoFactor = true)
        if (outcome.result == AuthResult.SUCCESS && outcome.userId != null && config.enabled) {
            call.startSession(sessionService, cookies, outcome.userId, SessionMethod.PASSWORD)
        }
        val code = if (outcome.result == AuthResult.SUCCESS) HttpStatusCode.OK else HttpStatusCode.Unauthorized
        call.respond(code, mapOf("result" to outcome.result))
    }

    post("/logout") {
        if (config.enabled) {
            cookies.sessionToken(call)?.let { sessionService.delete(it) }
            cookies.clearSession(call)
        }
        call.respond(HttpStatusCode.OK)
    }

    route("/auth/oidc") {

        get("/login") {
            if (!oidcService.enabled) throw NotFoundException("Single sign-on is not enabled")
            val returnTo = ReturnTo.sanitize(call.request.queryParameters["returnTo"], Environment.server.rootPath)
            val start = try {
                oidcService.start(returnTo)
            } catch (_: OidcUnavailableException) {
                call.respondRedirect(failureRedirect(SsoFailure.UNAVAILABLE))
                return@get
            }
            cookies.setLoginState(call, start.login)
            call.respondRedirect(start.authorizationUrl)
        }

        get("/callback") {
            if (!oidcService.enabled) throw NotFoundException("Single sign-on is not enabled")
            val params = call.request.queryParameters
            val pending = cookies.loginState(call)
            cookies.clearLoginState(call)
            val outcome = oidcService.complete(params["state"], pending, params["code"], params["error"])
            val redirect = when (outcome) {
                is SsoOutcome.SignedIn -> {
                    call.startSession(sessionService, cookies, outcome.userId, SessionMethod.OIDC)
                    outcome.returnTo
                }

                is SsoOutcome.Failed -> failureRedirect(outcome.failure)
            }
            call.respondRedirect(redirect)
        }
    }
}

fun Route.authProtected(sessionService: SessionService) {

    route("/user/sessions") {

        get {
            call.respond(sessionService.list(call.userId(), call.sessionId()))
        }

        delete {
            sessionService.revokeOthers(call.userId(), call.sessionId())
            call.respond(HttpStatusCode.OK)
        }

        delete("/{id}") {
            val id = call.parameters["id"] ?: throw NotFoundException("Session not found")
            if (!sessionService.revoke(call.userId(), id)) throw NotFoundException("Session not found")
            call.respond(HttpStatusCode.OK)
        }
    }
}

fun ApplicationCall.sessionId(): String? = principal<UserPrincipal>()?.sessionId

private fun ApplicationCall.startSession(
    sessionService: SessionService,
    cookies: AuthCookies,
    userId: UserId,
    method: SessionMethod
) {
    // whatever this browser was signed in as before is replaced, not left valid behind the new cookie
    cookies.sessionToken(this)?.let { sessionService.delete(it) }
    val session = sessionService.create(userId, method, request.userAgent(), request.origin.remoteAddress)
    cookies.setSession(this, session.token)
}

private fun failureRedirect(failure: SsoFailure): String = "$LOGIN_PAGE?sso=${failure.code}"
