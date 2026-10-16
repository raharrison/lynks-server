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
import lynks.common.exception.UnauthorizedException
import lynks.user.*
import lynks.util.userId

data class AuthConfigResponse(val passwordLogin: Boolean, val sso: SsoConfig?)
data class SsoConfig(val label: String)
data class AppSignInResponse(val result: AuthResult, val token: String?)
data class SsoExchangeRequest(val code: String, val verifier: String)
data class SsoExchangeResponse(val token: String)

private const val LOGIN_PAGE = "/login"

// registered by the Android app, which a single sign-on in a browser tab hands back to
private const val APP_RETURN = "net.ryanh.lynks://sso"

// the app's own sign in, which answers with the token instead of setting a cookie
val APP_SIGN_IN_PATHS = setOf("/auth/app/login", "/auth/oidc/exchange")

fun Route.authUnprotected(
    config: Environment.Auth,
    userService: UserService,
    sessionService: SessionService,
    oidcService: OidcService,
    handoffs: SsoHandoffs,
    cookies: AuthCookies
) {

    get("/auth/config") {
        val sso = if (config.oidc.enabled) SsoConfig(config.oidc.label) else null
        call.respond(AuthConfigResponse(config.passwordLoginEnabled, sso))
    }

    post("/login") {
        val outcome = call.checkPassword(config, userService) ?: return@post
        if (outcome.userId != null && config.enabled) {
            call.startSession(sessionService, cookies, outcome.userId, SessionMethod.PASSWORD)
        }
        call.respond(outcome.status(), mapOf("result" to outcome.result))
    }

    post("/auth/app/login") {
        val outcome = call.checkPassword(config, userService) ?: return@post
        val token = if (outcome.userId != null && config.enabled) {
            call.createSession(sessionService, outcome.userId, SessionMethod.PASSWORD)
        } else null
        call.respond(outcome.status(), AppSignInResponse(outcome.result, token))
    }

    post("/logout") {
        if (config.enabled) {
            val bearer = call.bearerToken()
            if (bearer != null) {
                sessionService.delete(bearer)
            } else {
                cookies.sessionToken(call)?.let { sessionService.delete(it) }
                cookies.clearSession(call)
            }
        }
        call.respond(HttpStatusCode.OK)
    }

    route("/auth/oidc") {

        get("/login") {
            if (!oidcService.enabled) throw NotFoundException("Single sign-on is not enabled")
            val params = call.request.queryParameters
            val challenge = params["challenge"]
            if (challenge != null && !SsoHandoffs.isChallenge(challenge)) {
                throw BadRequestException("An app sign in needs an S256 challenge")
            }
            val returnTo = ReturnTo.sanitize(params["returnTo"], Environment.server.rootPath)
            val start = try {
                oidcService.start(returnTo, challenge)
            } catch (_: OidcUnavailableException) {
                call.respondFailure(challenge != null, SsoFailure.UNAVAILABLE)
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
            // from the query rather than the cookie, which a login started elsewhere in the same browser may have replaced
            val forApp = PendingLogin.isAppState(params["state"])
            when (val outcome = oidcService.complete(params["state"], pending, params["code"], params["error"])) {
                is SsoOutcome.SignedIn -> {
                    val challenge = pending?.appChallenge
                    if (challenge != null) {
                        call.respondAppReturn("$APP_RETURN?code=${handoffs.issue(outcome.userId, challenge)}")
                    } else {
                        call.startSession(sessionService, cookies, outcome.userId, SessionMethod.OIDC)
                        // checked again, as outside prod a sibling subdomain could have planted the state cookie
                        call.respondRedirect(ReturnTo.sanitize(outcome.returnTo, Environment.server.rootPath))
                    }
                }

                is SsoOutcome.Failed -> call.respondFailure(forApp, outcome.failure)
            }
        }

        post("/exchange") {
            if (!oidcService.enabled) throw NotFoundException("Single sign-on is not enabled")
            val request = call.receive<SsoExchangeRequest>()
            val userId = handoffs.redeem(request.code, request.verifier)
                ?: throw UnauthorizedException("Sign in expired. Try again.")
            call.respond(SsoExchangeResponse(call.createSession(sessionService, userId, SessionMethod.OIDC)))
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

// null once it has answered, when password sign in is switched off
private suspend fun ApplicationCall.checkPassword(config: Environment.Auth, userService: UserService): AuthOutcome? {
    if (!config.passwordLoginEnabled) {
        respond(HttpStatusCode.Forbidden, ErrorResponse("Password sign in is disabled"))
        return null
    }
    return userService.checkAuth(receive<AuthRequest>(), twoFactor = true)
}

private fun AuthOutcome.status() = if (result == AuthResult.SUCCESS) HttpStatusCode.OK else HttpStatusCode.Unauthorized

private fun ApplicationCall.startSession(
    sessionService: SessionService,
    cookies: AuthCookies,
    userId: UserId,
    method: SessionMethod
) {
    // whatever this browser was signed in as before is replaced, not left valid behind the new cookie
    cookies.sessionToken(this)?.let { sessionService.delete(it) }
    cookies.setSession(this, createSession(sessionService, userId, method))
}

private fun ApplicationCall.createSession(sessionService: SessionService, userId: UserId, method: SessionMethod): String =
    sessionService.create(userId, method, request.userAgent(), request.origin.remoteAddress).token

private suspend fun ApplicationCall.respondFailure(forApp: Boolean, failure: SsoFailure) {
    if (forApp) respondAppReturn("$APP_RETURN?error=${failure.code}")
    else respondRedirect("$LOGIN_PAGE?sso=${failure.code}")
}

// A page rather than a redirect, because a browser may refuse to open an app without a tap, and then the link is the
// way back. Only base64url codes and fixed failure codes are interpolated, so nothing needs escaping.
private suspend fun ApplicationCall.respondAppReturn(target: String) {
    response.header(HttpHeaders.CacheControl, "no-store")
    respondText(
        """
        <!doctype html>
        <html lang="en">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta http-equiv="refresh" content="0;url=$target">
        <title>Lynks</title>
        </head>
        <body style="font-family: system-ui, sans-serif; text-align: center; padding: 3rem 1rem">
        <p>Returning to Lynks...</p>
        <p><a href="$target">Open Lynks</a></p>
        </body>
        </html>
        """.trimIndent(),
        ContentType.Text.Html
    )
}
