package user

import common.exception.InvalidModelException
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

    post("/user/preferences") {
        val preferences = call.receive<Preferences>()
        preferences.email?.let { email ->
            if (!URLUtils.isValidEmail(email)) {
                throw InvalidModelException("Invalid email address")
            }
        }
        val updated = userService.updateUserPreferences(preferences)
        workerRegistry.onUserPreferenceChange(updated)
        call.respond(HttpStatusCode.Accepted, updated)
    }

}