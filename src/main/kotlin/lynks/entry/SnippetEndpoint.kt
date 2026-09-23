package lynks.entry

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import lynks.common.EntryId
import lynks.common.NewSnippet
import lynks.common.exception.InvalidModelException
import lynks.common.exception.NotFoundException
import lynks.util.pageRequest
import lynks.util.versionParameter

fun Route.snippet(snippetService: SnippetService) {

    route("/snippet") {

        get {
            call.respond(snippetService.get(call.pageRequest()))
        }

        get("/{id}") {
            val snippet = snippetService.get(EntryId(call.parameters["id"] ?: throw InvalidModelException("Missing id"))) ?: throw NotFoundException()
            call.respond(snippet)
        }

        get("/{id}/{version}") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            val snippet = snippetService.get(EntryId(id), call.versionParameter()) ?: throw NotFoundException()
            call.respond(snippet)
        }

        post("/{id}/revert/{version}") {
            val id = EntryId(call.parameters["id"] ?: throw InvalidModelException("Missing id"))
            val reverted = snippetService.revert(id, call.versionParameter()) ?: throw NotFoundException()
            call.respond(HttpStatusCode.OK, reverted)
        }

        post {
            val snippet = call.receive<NewSnippet>()
            call.respond(HttpStatusCode.Created, snippetService.add(snippet))
        }

        put {
            val snippet = call.receive<NewSnippet>()
            val newVersion = call.parameters["newVersion"]?.let { it.toBoolean() } ?: true
            val updated = snippetService.update(snippet, newVersion) ?: throw NotFoundException()
            call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            if (!snippetService.delete(EntryId(call.parameters["id"] ?: throw InvalidModelException("Missing id")))) throw NotFoundException()
            call.respond(HttpStatusCode.OK)
        }

    }
}
