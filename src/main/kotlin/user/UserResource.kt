package user

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import util.URLUtils
import worker.WorkerRegistry

fun Route.user(userService: UserService, workerRegistry: WorkerRegistry) {

    get("/user/preferences") {
        call.respond(userService.currentUserPreferences)
    }

    post("/user/preferences") { _ ->
        val preferences = call.receive<Preferences>()
        preferences.email?.let {
            if (!URLUtils.isValidEmail(it)) {
                call.respond(HttpStatusCode.BadRequest, "Invalid email address")
                return@post
            }
        }
        val updated = userService.updateUserPreferences(preferences)
        workerRegistry.onUserPreferenceChange(updated)
        call.respond(HttpStatusCode.Accepted, updated)
    }

}