package entry

import common.NewNote
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import util.pageRequest

fun Route.note(noteService: NoteService) {

    route("/note") {

        get {
            call.respond(noteService.get(call.pageRequest()))
        }

        get("/{id}") {
            val note = noteService.get(call.parameters["id"]!!)
            if (note == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(note)
        }

        get("/{id}/{version}") {
            val id = call.parameters["id"]!!
            val version = call.parameters["version"]!!
            val note = noteService.get(id, version.toInt())
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
            val removed = noteService.delete(call.parameters["id"]!!)
            if (removed) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.NotFound)
        }

    }
}
