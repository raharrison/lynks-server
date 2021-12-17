package lynks.user

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import lynks.common.exception.InvalidModelException
import lynks.util.URLUtils
import lynks.worker.WorkerRegistry

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
