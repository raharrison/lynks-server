package lynks.user

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import lynks.common.ConfigMode
import lynks.common.Environment
import lynks.common.ErrorResponse
import lynks.common.UserSession
import lynks.common.exception.InvalidModelException
import lynks.common.exception.NotFoundException
import lynks.util.isCallAuthorizedForUser
import lynks.util.pageRequest

fun Route.userProtected(userService: UserService) {

    route("/user") {

        get {
            val username = call.principal<UserSession>()?.username ?:
                if(Environment.mode != ConfigMode.PROD) {
                    Environment.auth.defaultUserName
                }
                else {
                    return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
                }

            if (call.isCallAuthorizedForUser(username)) {
                val user = userService.getUser(username)
                if (user == null) call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
                else call.respond(HttpStatusCode.OK, user)
            } else {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
            }
        }

        get("/{id}") {
            val username = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            if (call.isCallAuthorizedForUser(username)) {
                val user = userService.getUser(username)
                if (user == null) call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
                else call.respond(HttpStatusCode.OK, user)
            } else {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
            }
        }

        post("/changePassword") {
            val changeRequest = call.receive<ChangePasswordRequest>()
            if (call.isCallAuthorizedForUser(changeRequest.username)) {
                val changed = userService.changePassword(changeRequest)
                if (changed) call.respond(HttpStatusCode.OK)
                else call.respond(HttpStatusCode.BadRequest, ErrorResponse("Old password is not correct"))
            } else {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
            }
        }

        put {
            val user = call.receive<UserUpdateRequest>()
            if (call.isCallAuthorizedForUser(user.username)) {
                val updated = userService.updateUser(user) ?: throw NotFoundException()
                call.respond(HttpStatusCode.OK, updated)
            } else {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
            }
        }

        get("/activity") {
            val username = call.principal<UserSession>()?.username ?:
            if(Environment.mode != ConfigMode.PROD) {
                Environment.auth.defaultUserName
            }
            else {
                return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
            }
            if (call.isCallAuthorizedForUser(username)) {
                val page = call.pageRequest()
                call.respond(userService.getUserActivityLog(page))
            } else {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
            }
        }

    }
}

fun Route.userUnprotected(userService: UserService) {

    post("/login") {
        val request = call.receive<AuthRequest>()
        val result = userService.checkAuth(request, twoFactor = true)
        if (result == AuthResult.SUCCESS) {
            if(Environment.auth.enabled) {
                call.sessions.set(UserSession(request.username))
            }
        }
        val code = if(result == AuthResult.SUCCESS) HttpStatusCode.OK else HttpStatusCode.Unauthorized
        call.respond(code, mapOf("result" to result))
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
