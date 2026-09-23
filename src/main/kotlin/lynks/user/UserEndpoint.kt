package lynks.user

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import lynks.common.Environment
import lynks.common.ErrorResponse
import lynks.common.UserSession
import lynks.common.exception.UnauthorizedException
import lynks.util.pageRequest
import lynks.util.userId

fun Route.userProtected(userService: UserService) {

    route("/user") {

        get {
            val user = userService.getUser(call.userId()) ?: throw UnauthorizedException()
            call.respond(HttpStatusCode.OK, user)
        }

        post("/changePassword") {
            val changeRequest = call.receive<ChangePasswordRequest>()
            if (userService.changePassword(call.userId(), changeRequest)) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.BadRequest, ErrorResponse("Old password is not correct"))
        }

        put {
            val update = call.receive<UserUpdateRequest>()
            val updated = userService.updateUser(call.userId(), update) ?: throw UnauthorizedException()
            call.respond(HttpStatusCode.OK, updated)
        }

        put("/jolt") {
            val request = call.receive<JoltTokenRequest>()
            val updated = userService.updateJoltToken(call.userId(), request.token) ?: throw UnauthorizedException()
            call.respond(HttpStatusCode.OK, updated)
        }

        get("/activity") {
            call.respond(userService.getUserActivityLog(call.userId(), call.pageRequest()))
        }

    }
}

fun Route.userUnprotected(userService: UserService) {

    post("/login") {
        val request = call.receive<AuthRequest>()
        val outcome = userService.checkAuth(request, twoFactor = true)
        if (outcome.result == AuthResult.SUCCESS && outcome.userId != null && Environment.auth.enabled) {
            call.sessions.set(UserSession(outcome.userId.value))
        }
        val code = if (outcome.result == AuthResult.SUCCESS) HttpStatusCode.OK else HttpStatusCode.Unauthorized
        call.respond(code, mapOf("result" to outcome.result))
    }

    post("/logout") {
        if (Environment.auth.enabled) {
            call.sessions.clear<UserSession>()
        }
        call.respond(HttpStatusCode.OK)
    }

    post("/user/register") {
        if (!Environment.auth.registrationsEnabled) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("Registrations disabled"))
            return@post
        }
        val registerRequest = call.receive<AuthRequest>()
        val createdUsername = userService.register(registerRequest)
        val response = mapOf("username" to createdUsername)
        call.respond(HttpStatusCode.Created, response)
    }

}
