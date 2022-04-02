package lynks.user

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import lynks.common.ConfigMode
import lynks.common.Environment
import lynks.common.exception.InvalidModelException
import lynks.util.URLUtils

fun Route.user(userService: UserService) {

    route("/user") {

        get("/{id}") {
            val username = call.parameters["id"]!!
            val user = userService.getUser(username)
            if (user == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.OK, user)
        }

        post("/register") {
            if(Environment.mode == ConfigMode.PROD) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }
            val registerRequest = call.receive<AuthRequest>()
            val created = userService.register(registerRequest)
            call.respond(HttpStatusCode.Created, created)
        }

        post("/changePassword") {
            val changeRequest = call.receive<ChangePasswordRequest>()
            val changed = userService.changePassword(changeRequest)
            if (changed) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.BadRequest)
        }

        put {
            val user = call.receive<User>()
            user.email?.let { email ->
                if (!URLUtils.isValidEmail(email)) {
                    throw InvalidModelException("Invalid email address")
                }
            }
            val updated = userService.updateUser(user)
            if (updated == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.OK, updated)
        }

    }

}
