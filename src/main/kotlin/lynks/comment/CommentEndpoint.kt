package lynks.comment

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import lynks.common.CommentId
import lynks.common.EntryId
import lynks.common.exception.InvalidModelException
import lynks.common.exception.NotFoundException
import lynks.util.pageRequest

fun Route.comment(commentService: CommentService) {

    route("/entry/{entryId}/comments") {

        get {
            val page = call.pageRequest()
            val entryId = EntryId(call.parameters["entryId"] ?: throw InvalidModelException("Missing entryId"))
            call.respond(commentService.getCommentsFor(entryId, page))
        }

        get("/{id}") {
            val commentId = CommentId(call.parameters["id"] ?: throw InvalidModelException("Missing id"))
            val entryId = EntryId(call.parameters["entryId"] ?: throw InvalidModelException("Missing entryId"))
            val comment = commentService.getComment(entryId, commentId) ?: throw NotFoundException()
            call.respond(comment)
        }

        post {
            val comment = call.receive<NewComment>()
            val entryId = EntryId(call.parameters["entryId"] ?: throw InvalidModelException("Missing entryId"))
            call.respond(HttpStatusCode.Created, commentService.addComment(entryId, comment))
        }

        put {
            val comment = call.receive<NewComment>()
            val entryId = EntryId(call.parameters["entryId"] ?: throw InvalidModelException("Missing entryId"))
            val updated = commentService.updateComment(entryId, comment) ?: throw NotFoundException()
            call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val commentId = CommentId(call.parameters["id"] ?: throw InvalidModelException("Missing id"))
            val entryId = EntryId(call.parameters["entryId"] ?: throw InvalidModelException("Missing entryId"))
            if (!commentService.deleteComment(entryId, commentId)) throw NotFoundException()
            call.respond(HttpStatusCode.OK)
        }

    }
}
