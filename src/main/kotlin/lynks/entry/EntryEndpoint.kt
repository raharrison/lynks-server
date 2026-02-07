package lynks.entry

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import lynks.common.EntryId
import lynks.entry.ref.EntryRefService
import lynks.group.GroupIdSet
import lynks.reminder.ReminderService
import lynks.util.pageRequest

fun Route.entry(
    entryService: EntryService,
    reminderService: ReminderService,
    entryAuditService: EntryAuditService,
    entryRefService: EntryRefService
) {

    route("/entry") {

        get {
            call.respond(entryService.get(call.pageRequest()))
        }

        get("/{id}") {
            val entry = entryService.get(EntryId(call.parameters["id"]!!))
            if (entry == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(entry)
        }

        get("/{id}/{version}") {
            val id = call.parameters["id"]!!
            val version = call.parameters["version"]!!
            val entry = entryService.get(EntryId(id), version.toInt())
            if (entry == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(entry)
        }

        get("/search") {
            val query = call.request.queryParameters["q"]
            if (query == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(entryService.search(query, call.pageRequest()))
        }

        get("/{id}/reminder") {
            val id = call.parameters["id"]!!
            call.respond(reminderService.getRemindersForEntry(EntryId(id)))
        }

        get("/{id}/history") {
            val id = call.parameters["id"]!!
            call.respond(entryService.getEntryVersions(EntryId(id)))
        }

        get("/{id}/audit") {
            val id = call.parameters["id"]!!
            call.respond(entryAuditService.getEntryAudit(EntryId(id)))
        }

        post("/{id}/star") {
            val id = call.parameters["id"]!!
            val updated = entryService.star(EntryId(id), true)
            if (updated == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(updated)
        }

        post("/{id}/unstar") {
            val id = call.parameters["id"]!!
            val updated = entryService.star(EntryId(id), false)
            if (updated == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(updated)
        }

        put("/{id}/groups") {
            val id = call.parameters["id"]!!
            val groupIds = call.receive<GroupIdSet>()
            if (entryService.updateEntryGroups(EntryId(id), groupIds.tags, groupIds.collections)) call.respond(HttpStatusCode.OK)
            call.respond(HttpStatusCode.NotFound)
        }

        get("/{id}/refs") {
            val id = call.parameters["id"]!!
            call.respond(entryRefService.getRefsForEntry(EntryId(id)))
        }
    }
}
