package lynks.entry

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import lynks.common.EntryId
import lynks.common.NewLink
import lynks.common.exception.InvalidModelException
import lynks.common.exception.NotFoundException
import lynks.util.URLUtils
import lynks.util.pageRequest

fun Route.link(linkService: LinkService) {

    fun checkLink(link: NewLink): Boolean = URLUtils.isValidUrl(link.url)

    route("/link") {

        get {
            call.respond(linkService.get(call.pageRequest()))
        }

        get("/{id}") {
            val link = linkService.get(EntryId(call.parameters["id"] ?: throw InvalidModelException("Missing id"))) ?: throw NotFoundException()
            call.respond(link)
        }

        get("/{id}/{version}") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            val version = call.parameters["version"] ?: throw InvalidModelException("Missing version")
            val link = linkService.get(EntryId(id), version.toInt()) ?: throw NotFoundException()
            call.respond(link)
        }

        post {
            val link = call.receive<NewLink>()
            if (!checkLink(link)) throw InvalidModelException("Invalid URL")
            else call.respond(HttpStatusCode.Created, linkService.add(link))
        }

        put {
            val link = call.receive<NewLink>()
            if (!checkLink(link)) throw InvalidModelException("Invalid URL")
            val newVersion = call.parameters["newVersion"]?.toBoolean() ?: true
            val updated = linkService.update(link, newVersion) ?: throw NotFoundException()
            call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            if (!linkService.delete(EntryId(call.parameters["id"] ?: throw InvalidModelException("Missing id")))) throw NotFoundException()
            call.respond(HttpStatusCode.OK)
        }

        post("/{id}/read") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            val updated = linkService.read(EntryId(id), true) ?: throw NotFoundException()
            call.respond(updated)
        }

        post("/{id}/unread") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            val updated = linkService.read(EntryId(id), false) ?: throw NotFoundException()
            call.respond(updated)
        }

        post("/{id}/content") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            val content = call.receive<String>()
            val updatedContent = linkService.updateSearchableContent(EntryId(id), content) ?: throw NotFoundException()
            call.respond(mapOf("content" to updatedContent))
        }

        get("/{id}/launch") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            val read = linkService.read(EntryId(id), true) ?: throw NotFoundException()
            call.respondRedirect(read.url)
        }

        post("/checkExisting") {
            val url = call.receive<String>()
            if(!URLUtils.isValidUrl(url)) throw InvalidModelException("Invalid URL")
            else call.respond(linkService.checkExistingWithUrl(url))
        }
    }
}
