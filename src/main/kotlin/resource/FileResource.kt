package resource

import common.Environment
import io.ktor.application.call
import io.ktor.content.PartData
import io.ktor.content.files
import io.ktor.content.forEachPart
import io.ktor.content.static
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveMultipart
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.response.respondFile
import io.ktor.routing.*

fun Route.resources(resourceManager: ResourceManager) {

    static("temp") {
        files(Environment.resourceTempPath)
    }

    route("/entry/{entryId}/resources") {

        get("/") {
            val id = call.parameters["entryId"]!!
            call.respond(resourceManager.getResourcesFor(id))
        }

        get("/{id}") {
            val res = resourceManager.getResourceAsFile(call.parameters["id"]!!)
            if (res != null) {
                call.response.header("Content-Disposition", "attachment; filename=\"${res.name}\"")
                call.respondFile(res)
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
            if(res == null) call.respond(HttpStatusCode.BadRequest)
            else call.respond(HttpStatusCode.Created, res!!)
        }

        delete("/{id}") {
            val removed = resourceManager.delete(call.parameters["id"]!!)
            if (removed) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.NotFound)
        }

    }
}