package lynks.entry

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import lynks.common.EntryId
import lynks.common.NewLink
import lynks.common.exception.InvalidModelException
import lynks.util.URLUtils
import lynks.util.pageRequest

fun Route.link(linkService: LinkService) {

    fun checkLink(link: NewLink): Boolean = URLUtils.isValidUrl(link.url)

    route("/link") {

        get {
            call.respond(linkService.get(call.pageRequest()))
        }

        get("/{id}") {
            val link = linkService.get(EntryId(call.parameters["id"] ?: throw InvalidModelException("Missing id")))
            if (link == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(link)
        }

        get("/{id}/{version}") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            val version = call.parameters["version"] ?: throw InvalidModelException("Missing version")
            val link = linkService.get(EntryId(id), version.toInt())
            if (link == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(link)
        }

        post {
            val link = call.receive<NewLink>()
            if (!checkLink(link)) throw InvalidModelException("Invalid URL")
            else call.respond(HttpStatusCode.Created, linkService.add(link))
        }

        put {
            val link = call.receive<NewLink>()
            val newVersion = call.parameters["newVersion"]?.toBoolean() ?: true
            val updated = linkService.update(link, newVersion)
            if (!checkLink(link)) throw InvalidModelException("Invalid URL")
            else {
                if (updated == null) call.respond(HttpStatusCode.NotFound)
                else call.respond(HttpStatusCode.OK, updated)
            }
        }

        delete("/{id}") {
            val removed = linkService.delete(EntryId(call.parameters["id"] ?: throw InvalidModelException("Missing id")))
            if (removed) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.NotFound)
        }

        post("/{id}/read") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            val updated = linkService.read(EntryId(id), true)
            if (updated == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(updated)
        }

        post("/{id}/unread") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            val updated = linkService.read(EntryId(id), false)
            if (updated == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(updated)
        }

        post("/{id}/content") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            val content = call.receive<String>()
            val updatedContent = linkService.updateSearchableContent(EntryId(id), content)
            if (updatedContent == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(mapOf("content" to updatedContent))
        }

        get("/{id}/launch") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            val read = linkService.read(EntryId(id), true)
            if (read == null) call.respond(HttpStatusCode.NotFound)
            else call.respondRedirect(read.url)
        }

        post("/checkExisting") {
            val url = call.receive<String>()
            if(!URLUtils.isValidUrl(url)) throw InvalidModelException("Invalid URL")
            else call.respond(linkService.checkExistingWithUrl(url))
        }
    }
}
