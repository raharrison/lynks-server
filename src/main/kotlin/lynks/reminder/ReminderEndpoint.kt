package lynks.reminder

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.reminder(reminderService: ReminderService) {

    route("/reminder") {

        get {
            call.respond(reminderService.getAllReminders())
        }

        get("/{id}") {
            val id = call.parameters["id"]!!
            val reminder = reminderService.get(id)
            if (reminder == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(reminder)
        }

        post {
            val reminder = call.receive<NewReminder>()
            call.respond(HttpStatusCode.Created, reminderService.addReminder(reminder))
        }

        put {
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

        post("/validate") {
            val scheduleDef = call.receive<String>()
            call.respond(HttpStatusCode.OK, reminderService.validateAndTranscribeSchedule(scheduleDef))
        }

    }

}
