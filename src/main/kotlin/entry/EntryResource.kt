package entry

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import reminder.ReminderService
import util.pageRequest

fun Route.entry(entryService: EntryService, reminderService: ReminderService) {

    route("/entry") {

        get("/") {
            call.respond(entryService.get(call.pageRequest()))
        }

        get("/{id}") {
            val entry = entryService.get(call.parameters["id"]!!)
            if (entry == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(entry)
        }

        get("/{id}/{version}") {
            val id = call.parameters["id"]!!
            val version = call.parameters["version"]!!
            val entry = entryService.get(id, version.toInt())
            if (entry == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(entry)
        }

        get("/search") {
            val query = call.request.queryParameters["q"]
            if(query == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(entryService.search(query, call.pageRequest()))
        }

        get("/{id}/reminder") {
            val id = call.parameters["id"]!!
            call.respond(reminderService.getRemindersForEntry(id))
        }

        get("/{id}/history") {
            val id = call.parameters["id"]!!
            call.respond(entryService.getEntryVersions(id))
        }

        post("/{id}/star") {
            val id = call.parameters["id"]!!
            val updated = entryService.star(id, true)
            if (updated == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(updated)
        }

        post("/{id}/unstar") {
            val id = call.parameters["id"]!!
            val updated = entryService.star(id, false)
            if (updated == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(updated)
        }
    }
}
