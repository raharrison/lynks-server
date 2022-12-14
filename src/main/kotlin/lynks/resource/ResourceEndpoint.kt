package lynks.resource

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import lynks.common.Environment
import lynks.common.IMAGE_UPLOAD_BASE
import lynks.common.MAX_IMAGE_UPLOAD_BYTES
import lynks.common.TEMP_URL
import lynks.common.exception.InvalidModelException
import lynks.util.FileUtils
import lynks.util.HashUtils
import java.io.File
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

fun Route.resource(resourceManager: ResourceManager) {

    static("temp") {
        files(Environment.resource.resourceTempPath)
    }

    fun deriveMimeType(filename: String): String {
        val contentType = ContentType.defaultForFilePath(filename)
        return contentType.withoutParameters().toString()
    }

    route("/imageUpload") {
        post {
            val multipart = call.receiveMultipart()
            var partData: PartData.FileItem? = null
            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    partData = part
                } else {
                    part.dispose()
                }
            }
            partData?.let {
                val extension = FileUtils.getExtension(partData!!.originalFileName!!)
                if (listOf("jpg", "jpeg", "png").contains(extension.lowercase())) {
                    val data = partData!!.streamProvider().readBytes()
                    if (data.size > MAX_IMAGE_UPLOAD_BYTES) {
                        call.respond(HttpStatusCode.PayloadTooLarge, ImageUploadErrorResponse("fileTooLarge"))
                    } else {
                        val file = resourceManager.saveTempFile(IMAGE_UPLOAD_BASE, data, ResourceType.UPLOAD, extension)
                        val uploadFilePath =
                            "${TEMP_URL}${resourceManager.constructTempUrlFromPath(file)}"
                        partData!!.dispose()
                        call.respond(HttpStatusCode.OK, ImageUploadResponse(ImageUploadFilePath(uploadFilePath)))
                    }
                } else {
                    call.respond(HttpStatusCode.UnsupportedMediaType, ImageUploadErrorResponse("typeNotAllowed"))
                }
            } ?: call.respond(HttpStatusCode.BadRequest, ImageUploadErrorResponse("noFileGiven"))
        }
    }

    route("/entry/{entryId}/resource") {

        val cacheExpiresAge = LocalDate.now().plusYears(5).atStartOfDay()
            .with(TemporalAdjusters.firstDayOfYear())
        // used when retrieving resource files to prevent lookups
        val resourceCache = mutableMapOf<String, Pair<Resource, File>>()

        get {
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
            val id = call.parameters["id"]!!
            val res = if(id in resourceCache) resourceCache[id] else {
                resourceManager.getResourceAsFile(id)?.also {
                    resourceCache[id] = it
                }
            }
            if (res != null) {
                call.response.header(HttpHeaders.ContentDisposition, "inline; filename=\"${res.first.name}\"")
                call.response.header(HttpHeaders.Expires, cacheExpiresAge)
                call.response.header(HttpHeaders.ETag, HashUtils.sha1Hash(res.first.dateCreated.toString()))
                call.respondFile(res.second)
            } else call.respond(HttpStatusCode.NotFound)
        }

        post {
            val entryId = call.parameters["entryId"]!!
            val multipart = call.receiveMultipart()
            var res: Resource? = null
            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    val name = part.originalFileName!!
                    res = resourceManager.saveUploadedResource(entryId, name, part.streamProvider())
                }
                part.dispose()
            }
            if (res == null) throw InvalidModelException()
            else call.respond(HttpStatusCode.Created, res!!)
        }

        put {
            val resource = call.receive<Resource>()
            val updated = resourceManager.updateResource(resource)
            if (updated == null) call.respond(HttpStatusCode.NotFound)
            else {
                resourceCache.remove(updated.id)
                call.respond(HttpStatusCode.OK, updated)
            }
        }

        delete("/{id}") {
            val id = call.parameters["id"]!!
            val removed = resourceManager.delete(id)
            if (removed) {
                resourceCache.remove(id)
                call.respond(HttpStatusCode.OK)
            }
            else call.respond(HttpStatusCode.NotFound)
        }

    }
}
