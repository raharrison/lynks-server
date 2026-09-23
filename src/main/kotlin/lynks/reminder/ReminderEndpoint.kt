package lynks.reminder

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import lynks.common.ReminderId
import lynks.common.exception.InvalidModelException
import lynks.common.exception.NotFoundException
import lynks.util.pageRequest
import lynks.util.userId

fun Route.reminder(reminderService: ReminderService) {

    route("/reminder") {

        get {
            call.respond(reminderService.getAllReminders(call.userId(), call.pageRequest()))
        }

        get("/{id}") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            val reminder = reminderService.get(call.userId(), ReminderId(id)) ?: throw NotFoundException()
            call.respond(reminder)
        }

        post {
            val reminder = call.receive<NewReminder>()
            call.respond(HttpStatusCode.Created, reminderService.addReminder(call.userId(), reminder))
        }

        put {
            val reminder = call.receive<NewReminder>()
            val updated = reminderService.updateReminder(call.userId(), reminder) ?: throw NotFoundException()
            call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            if (!reminderService.delete(call.userId(), ReminderId(id))) throw NotFoundException()
            call.respond(HttpStatusCode.OK)
        }

        post("/validate") {
            val scheduleDef = call.receive<String>()
            call.respond(HttpStatusCode.OK, reminderService.validateAndTranscribeSchedule(scheduleDef))
        }

    }

}
