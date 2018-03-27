package entry

import common.NewNote
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.*
import util.pageRequest

fun Route.note(noteService: NoteService) {

    route("/note") {

        get("/") {
            call.respond(noteService.get(call.pageRequest()))
        }

        get("/{id}") {
            val link = noteService.get(call.parameters["id"]!!)
            if (link == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(link)
        }

        post("/") {
            val link = call.receive<NewNote>()
            call.respond(noteService.add(link))
        }

        put("/") {
            val link = call.receive<NewNote>()
            call.respond(noteService.update(link))
        }

        delete("/{id}") {
            val removed = noteService.delete(call.parameters["id"]!!)
            if (removed) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.NotFound)
        }

    }
}
