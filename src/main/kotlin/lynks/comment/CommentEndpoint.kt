package lynks.comment

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import lynks.common.CommentId
import lynks.common.EntryId
import lynks.util.pageRequest

fun Route.comment(commentService: CommentService) {

    route("/entry/{entryId}/comments") {

        get {
            val page = call.pageRequest()
            val entryId = EntryId(call.parameters["entryId"]!!)
            call.respond(commentService.getCommentsFor(entryId, page))
        }

        get("/{id}") {
            val commentId = CommentId(call.parameters["id"]!!)
            val entryId = EntryId(call.parameters["entryId"]!!)
            val comment = commentService.getComment(entryId, commentId)
            if (comment == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(comment)
        }

        post {
            val comment = call.receive<NewComment>()
            val entryId = EntryId(call.parameters["entryId"]!!)
            call.respond(HttpStatusCode.Created, commentService.addComment(entryId, comment))
        }

        put {
            val comment = call.receive<NewComment>()
            val entryId = EntryId(call.parameters["entryId"]!!)
            val updated = commentService.updateComment(entryId, comment)
            if (updated == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val commentId = CommentId(call.parameters["id"]!!)
            val entryId = EntryId(call.parameters["entryId"]!!)
            val removed = commentService.deleteComment(entryId, commentId)
            if (removed) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.NotFound)
        }

    }
}
