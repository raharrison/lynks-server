package lynks.entry

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import lynks.common.EntryId
import lynks.common.NewNote
import lynks.common.exception.InvalidModelException
import lynks.common.exception.NotFoundException
import lynks.util.pageRequest
import lynks.util.userId
import lynks.util.versionParameter

fun Route.note(noteService: NoteService) {

    route("/note") {

        get {
            call.respond(noteService.get(call.userId(), call.pageRequest()))
        }

        get("/{id}") {
            val note = noteService.get(call.userId(), EntryId(call.parameters["id"] ?: throw InvalidModelException("Missing id")))
                ?: throw NotFoundException()
            call.respond(note)
        }

        get("/{id}/{version}") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            val note = noteService.get(call.userId(), EntryId(id), call.versionParameter()) ?: throw NotFoundException()
            call.respond(note)
        }

        post("/{id}/revert/{version}") {
            val id = EntryId(call.parameters["id"] ?: throw InvalidModelException("Missing id"))
            val reverted = noteService.revert(call.userId(), id, call.versionParameter()) ?: throw NotFoundException()
            call.respond(HttpStatusCode.OK, reverted)
        }

        post {
            val note = call.receive<NewNote>()
            call.respond(HttpStatusCode.Created, noteService.add(call.userId(), note))
        }

        put {
            val note = call.receive<NewNote>()
            val newVersion = call.parameters["newVersion"]?.let { it.toBoolean() } ?: true
            val updated = noteService.update(call.userId(), note, newVersion) ?: throw NotFoundException()
            call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            if (!noteService.delete(
                    call.userId(),
                    EntryId(call.parameters["id"] ?: throw InvalidModelException("Missing id"))
                )
            ) throw NotFoundException()
            call.respond(HttpStatusCode.OK)
        }

    }
}
