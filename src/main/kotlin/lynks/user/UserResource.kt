package lynks.user

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import lynks.common.ConfigMode
import lynks.common.Environment
import lynks.common.UserSession
import lynks.common.exception.InvalidModelException
import lynks.util.URLUtils
import lynks.util.isCallAuthorizedForUser

fun Route.userProtected(userService: UserService) {

    route("/user") {

        get("/{id}") {
            val username = call.parameters["id"]!!
            if(call.isCallAuthorizedForUser(username)) {
                val user = userService.getUser(username)
                if (user == null) call.respond(HttpStatusCode.NotFound)
                else call.respond(HttpStatusCode.OK, user)
            } else {
                call.respond(UnauthorizedResponse())
            }
        }

        post("/register") {
            if (Environment.mode == ConfigMode.PROD) {
                // disable as new users would be able to access all entries
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }
            val registerRequest = call.receive<AuthRequest>()
            val created = userService.register(registerRequest)
            call.respond(HttpStatusCode.Created, created)
        }

        post("/changePassword") {
            val changeRequest = call.receive<ChangePasswordRequest>()
            if (call.isCallAuthorizedForUser(changeRequest.username)) {
                val changed = userService.changePassword(changeRequest)
                if (changed) call.respond(HttpStatusCode.OK)
                else call.respond(HttpStatusCode.BadRequest)
            } else {
                call.respond(UnauthorizedResponse())
            }
        }

        put {
            val user = call.receive<User>()
            if (call.isCallAuthorizedForUser(user.username)) {
                user.email?.let { email ->
                    if (!URLUtils.isValidEmail(email)) {
                        throw InvalidModelException("Invalid email address")
                    }
                }
                val updated = userService.updateUser(user)
                if (updated == null) call.respond(HttpStatusCode.NotFound)
                else call.respond(HttpStatusCode.OK, updated)
            } else {
                call.respond(UnauthorizedResponse())
            }
        }

    }
}

fun Route.userUnprotected(userService: UserService) {

    post("/login") {
        val request = call.receive<AuthRequest>()
        if (userService.checkAuth(request)) {
            call.sessions.set(UserSession(request.username))
            call.respond(HttpStatusCode.OK)
        } else {
            call.respond(HttpStatusCode.Unauthorized)
        }
    }

    post("/logout") {
        call.sessions.clear<UserSession>()
        call.respond(HttpStatusCode.OK)
    }

}
