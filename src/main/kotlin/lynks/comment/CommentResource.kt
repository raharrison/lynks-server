package lynks.comment

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import lynks.util.pageRequest

fun Route.comment(commentService: CommentService) {

    route("/entry/{entryId}/comments") {

        get {
            val page = call.pageRequest()
            val id = call.parameters["entryId"]!!
            call.respond(commentService.getCommentsFor(id, page))
        }

        get("/{id}") {
            val id = call.parameters["id"]!!
            val entryId = call.parameters["entryId"]!!
            val comment = commentService.getComment(entryId, id)
            if (comment == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(comment)
        }

        post {
            val comment = call.receive<NewComment>()
            val entryId = call.parameters["entryId"]!!
            call.respond(HttpStatusCode.Created, commentService.addComment(entryId, comment))
        }

        put {
            val comment = call.receive<NewComment>()
            val entryId = call.parameters["entryId"]!!
            val updated = commentService.updateComment(entryId, comment)
            if (updated == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val id = call.parameters["id"]!!
            val entryId = call.parameters["entryId"]!!
            val removed = commentService.deleteComment(entryId, id)
            if (removed) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.NotFound)
        }

    }
}
