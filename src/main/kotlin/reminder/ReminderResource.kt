package reminder

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.*

fun Route.reminder(reminderService: ReminderService) {

    route("/reminder") {

        get("/") {
            call.respond(reminderService.getAllReminders())
        }

        get("/{id}") {
            val id = call.parameters["id"]!!
            val reminder = reminderService.get(id)
            if (reminder == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(reminder)
        }

        post("/") {
            val reminder = call.receive<NewReminder>()
            call.respond(HttpStatusCode.Created, reminderService.addReminder(reminder))
        }

        put("/") {
            val reminder = call.receive<NewReminder>()
            val updated = reminderService.updateReminder(reminder)
            if (updated == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val id = call.parameters["id"]!!
            val removed = reminderService.delete(id)
            if (removed) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.NotFound)
        }

    }

}