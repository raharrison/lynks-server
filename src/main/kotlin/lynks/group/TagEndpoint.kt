package lynks.group

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import lynks.common.exception.InvalidModelException
import lynks.common.exception.NotFoundException
import lynks.util.userId

fun Route.tag(tagService: TagService) {

    route("/tag") {

        get {
            call.respond(tagService.getAll(call.userId()))
        }

        get("/{id}") {
            val tag = tagService.get(call.userId(), call.parameters["id"] ?: throw InvalidModelException("Missing id"))
                ?: throw NotFoundException()
            call.respond(tag)
        }

        post {
            val tag = call.receive<NewTag>()
            call.respond(HttpStatusCode.Created, tagService.add(call.userId(), tag))
        }

        put {
            val tag = call.receive<NewTag>()
            val updated = tagService.update(call.userId(), tag) ?: throw NotFoundException()
            call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            if (!tagService.delete(call.userId(), id)) throw NotFoundException()
            call.respond(HttpStatusCode.OK)
        }

        post("/refresh") {
            tagService.rebuild(call.userId())
            call.respond(HttpStatusCode.OK)
        }

    }
}
