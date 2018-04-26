package resource

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.delete
import io.ktor.routing.get
import io.ktor.routing.route

fun Route.resources(resourceManager: ResourceManager) {

    route("/entry/{entryId}/resources") {

        get("/") {
            val id = call.parameters["entryId"]!!
            call.respond(resourceManager.getResourcesFor(id))
        }
//
//        get("/{id}") {
//            val comment = commentService.getComment(call.parameters["id"]!!)
//            if (comment == null) call.respond(HttpStatusCode.NotFound)
//            else call.respond(comment)
//        }
//
//        post("/") {
//            val comment = call.receive<NewComment>()
//            val entryId = call.parameters["entryId"]!!
//            call.respond(commentService.addComment(entryId, comment))
//        }
//
        delete("/{id}") {
            val removed = resourceManager.delete(call.parameters["id"]!!)
            if (removed) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.NotFound)
        }

    }
}