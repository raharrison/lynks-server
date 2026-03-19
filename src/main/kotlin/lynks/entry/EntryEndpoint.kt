package lynks.entry

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import lynks.common.EntryId
import lynks.common.exception.InvalidModelException
import lynks.common.exception.NotFoundException
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
            val entry = entryService.get(EntryId(call.parameters["id"] ?: throw InvalidModelException("Missing id"))) ?: throw NotFoundException()
            call.respond(entry)
        }

        get("/{id}/{version}") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            val version = call.parameters["version"] ?: throw InvalidModelException("Missing version")
            val entry = entryService.get(EntryId(id), version.toInt()) ?: throw NotFoundException()
            call.respond(entry)
        }

        get("/search") {
            val query = call.request.queryParameters["q"] ?: throw InvalidModelException("Missing search query")
            call.respond(entryService.search(query, call.pageRequest()))
        }

        get("/suggest") {
            val query = call.request.queryParameters["q"] ?: throw InvalidModelException("Missing query")
            call.respond(entryService.suggest(query, call.pageRequest()))
        }

        get("/resolve") {
            val ids = call.request.queryParameters["ids"]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.map { EntryId(it) }
                ?: emptyList()
            call.respond(if (ids.isEmpty()) emptyList<Any>() else entryService.get(ids).content)
        }

        get("/{id}/reminder") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            call.respond(reminderService.getRemindersForEntry(EntryId(id)))
        }

        get("/{id}/history") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            call.respond(entryService.getEntryVersions(EntryId(id)))
        }

        get("/{id}/audit") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            call.respond(entryAuditService.getEntryAudit(EntryId(id)))
        }

        post("/{id}/star") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            val updated = entryService.star(EntryId(id), true) ?: throw NotFoundException()
            call.respond(updated)
        }

        post("/{id}/unstar") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            val updated = entryService.star(EntryId(id), false) ?: throw NotFoundException()
            call.respond(updated)
        }

        put("/{id}/groups") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            val groupIds = call.receive<GroupIdSet>()
            if (!entryService.updateEntryGroups(EntryId(id), groupIds.tags, groupIds.collections)) throw NotFoundException()
            call.respond(HttpStatusCode.OK)
        }

        get("/{id}/refs") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            call.respond(entryRefService.getRefsForEntry(EntryId(id)))
        }
    }
}
