package tag

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.*

fun Route.tag(tagService: TagService) {

    route("/tag") {

        get("/") {
            call.respond(tagService.getAllTags())
        }

        get("/{id}") {
            val tag = tagService.getTag(call.parameters["id"]!!)
            if (tag == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(tag)
        }

        post("/") {
            val tag = call.receive<NewTag>()
            call.respond(HttpStatusCode.Created, tagService.addTag(tag))
        }

        put("/") {
            val tag = call.receive<NewTag>()
            val updated = tagService.updateTag(tag)
            if(updated == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val id = call.parameters["id"]!!
            val removed = tagService.deleteTag(id)
            if (removed) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.NotFound)
        }

        post("/refresh") {
            tagService.rebuild()
            call.respond(HttpStatusCode.OK)
        }

    }
}