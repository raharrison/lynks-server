package lynks.user

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import lynks.auth.SessionService
import lynks.auth.sessionId
import lynks.common.ErrorResponse
import lynks.common.exception.UnauthorizedException
import lynks.util.pageRequest
import lynks.util.userId

fun Route.userProtected(userService: UserService, sessionService: SessionService) {

    route("/user") {

        get {
            val user = userService.getUser(call.userId()) ?: throw UnauthorizedException()
            call.respond(HttpStatusCode.OK, user)
        }

        post("/changePassword") {
            val changeRequest = call.receive<ChangePasswordRequest>()
            if (userService.changePassword(call.userId(), changeRequest)) {
                sessionService.revokeOthers(call.userId(), call.sessionId())
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Old password is not correct"))
            }
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
