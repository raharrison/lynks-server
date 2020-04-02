package resource

import common.Environment
import common.exception.InvalidModelException
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.*
import io.ktor.http.defaultForFilePath
import io.ktor.request.receive
import io.ktor.request.receiveMultipart
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.response.respondFile
import io.ktor.routing.*

fun Route.resources(resourceManager: ResourceManager) {

    static("temp") {
        files(Environment.server.resourceTempPath)
    }

    fun deriveMimeType(filename: String): String {
        val contentType = ContentType.defaultForFilePath(filename)
        return contentType.withoutParameters().toString()
    }

    route("/entry/{entryId}/resource") {

        get("/") {
            val id = call.parameters["entryId"]!!
            call.respond(resourceManager.getResourcesFor(id))
        }

        get("/{id}/info") {
            val id = call.parameters["id"]!!
            val resource = resourceManager.getResource(id)
            if (resource == null) call.respond(HttpStatusCode.NotFound)
            else {
                call.response.header("X-Resource-Mime-Type", deriveMimeType(resource.name))
                call.respond(resource)
            }
        }

        get("/{id}") {
            val res = resourceManager.getResourceAsFile(call.parameters["id"]!!)
            if (res != null) {
                call.response.header("Content-Disposition", "inline; filename=\"${res.first.name}\"")
                call.respondFile(res.second)
            }
            else call.respond(HttpStatusCode.NotFound)
        }

        post("/") {
            val entryId = call.parameters["entryId"]!!
            val multipart = call.receiveMultipart()
            var res: Resource? = null
            multipart.forEachPart { part ->
                if(part is PartData.FileItem) {
                    val name = part.originalFileName!!
                    res = resourceManager.saveUploadedResource(entryId, name, part.streamProvider())
                }
                part.dispose()
            }
            if(res == null) throw InvalidModelException()
            else call.respond(HttpStatusCode.Created, res!!)
        }

        put("/") {
            val resource = call.receive<Resource>()
            val updated = resourceManager.updateResource(resource)
            if(updated == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val removed = resourceManager.delete(call.parameters["id"]!!)
            if (removed) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.NotFound)
        }

    }
}