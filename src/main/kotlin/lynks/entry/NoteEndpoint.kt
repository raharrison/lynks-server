package lynks.entry

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import lynks.common.EntryId
import lynks.common.NewNote
import lynks.common.exception.InvalidModelException
import lynks.util.pageRequest

fun Route.note(noteService: NoteService) {

    route("/note") {

        get {
            call.respond(noteService.get(call.pageRequest()))
        }

        get("/{id}") {
            val note = noteService.get(EntryId(call.parameters["id"] ?: throw InvalidModelException("Missing id")))
            if (note == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(note)
        }

        get("/{id}/{version}") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            val version = call.parameters["version"] ?: throw InvalidModelException("Missing version")
            val note = noteService.get(EntryId(id), version.toInt())
            if (note == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(note)
        }

        post {
            val note = call.receive<NewNote>()
            call.respond(HttpStatusCode.Created, noteService.add(note))
        }

        put {
            val note = call.receive<NewNote>()
            val newVersion = call.parameters["newVersion"]?.let { it.toBoolean() } ?: true
            val updated = noteService.update(note, newVersion)
            if (updated == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val removed = noteService.delete(EntryId(call.parameters["id"] ?: throw InvalidModelException("Missing id")))
            if (removed) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.NotFound)
        }

    }
}
