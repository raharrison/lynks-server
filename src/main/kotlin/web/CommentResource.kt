package web

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.*
import model.NewComment
import service.CommentService
import util.pageRequest

fun Route.comment(commentService: CommentService) {

    route("/entry/{entryId}/comments") {

        get("/") {
            val page = call.pageRequest()
            val id = call.parameters["entryId"]!!
            call.respond(commentService.getCommentsFor(id, page))
        }

        get("/{id}") {
            val comment = commentService.getComment(call.parameters["id"]!!)
            if (comment == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(comment)
        }

        post("/") {
            val comment = call.receive<NewComment>()
            val entryId = call.parameters["entryId"]!!
            call.respond(commentService.addComment(entryId, comment))
        }

        put("/") {
            val comment = call.receive<NewComment>()
            val entryId = call.parameters["entryId"]!!
            call.respond(commentService.updateComment(entryId, comment))
        }

        delete("/{id}") {
            val removed = commentService.deleteComment(call.parameters["id"]!!)
            if (removed) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.NotFound)
        }

    }
}