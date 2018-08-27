package user

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import javax.mail.internet.AddressException
import javax.mail.internet.InternetAddress

fun Route.user(userService: UserService) {

    get("/user/preferences") {
        call.respond(userService.currentUserPreferences)
    }

    fun isValidEmail(email: String): Boolean {
        return try {
            InternetAddress(email).validate()
            true
        } catch (ex: AddressException) {
            false
        }
    }

    post("/user/preferences") { _ ->
        val preferences = call.receive<Preferences>()
        preferences.email?.let {
            if (!isValidEmail(it)) {
                call.respond(HttpStatusCode.BadRequest, "Invalid email address")
                return@post
            }
        }
        call.respond(HttpStatusCode.Accepted, userService.updateUserPreferences(preferences))
    }

}