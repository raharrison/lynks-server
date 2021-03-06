package group

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*

fun Route.tag(tagService: TagService) {

    route("/tag") {

        get {
            call.respond(tagService.getAll())
        }

        get("/{id}") {
            val tag = tagService.get(call.parameters["id"]!!)
            if (tag == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(tag)
        }

        post {
            val tag = call.receive<NewTag>()
            call.respond(HttpStatusCode.Created, tagService.add(tag))
        }

        put {
            val tag = call.receive<NewTag>()
            val updated = tagService.update(tag)
            if (updated == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val id = call.parameters["id"]!!
            val removed = tagService.delete(id)
            if (removed) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.NotFound)
        }

        post("/refresh") {
            tagService.rebuild()
            call.respond(HttpStatusCode.OK)
        }

    }
}
