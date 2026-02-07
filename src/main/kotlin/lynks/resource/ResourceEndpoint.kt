package lynks.resource

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.io.readByteArray
import lynks.common.*
import lynks.common.exception.InvalidModelException
import lynks.util.FileUtils
import lynks.util.HashUtils
import java.io.File
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.ConcurrentHashMap

fun Route.resource(resourceManager: ResourceManager) {

    staticFiles("temp", File(Environment.resource.resourceTempPath))

    fun deriveMimeType(filename: String): String {
        val contentType = ContentType.defaultForFilePath(filename)
        return contentType.withoutParameters().toString()
    }

    post("/imageUpload") {
        val multipart = call.receiveMultipart()

        var fileBytes: ByteArray? = null
        var fileName: String? = null

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    fileName = part.originalFileName
                    fileBytes = part.provider()
                        .readRemaining()
                        .readByteArray()
                    part.dispose()
                }
                else -> part.dispose()
            }
        }

        if (fileBytes == null || fileName == null) {
            call.respond(
                HttpStatusCode.BadRequest,
                ImageUploadErrorResponse("noFileGiven")
            )
            return@post
        }

        val extension = FileUtils.getExtension(fileName ?: throw InvalidModelException("Missing fileName")).lowercase()
        if (extension !in listOf("jpg", "jpeg", "png")) {
            call.respond(
                HttpStatusCode.UnsupportedMediaType,
                ImageUploadErrorResponse("typeNotAllowed")
            )
            return@post
        }

        if (fileBytes.size > MAX_IMAGE_UPLOAD_BYTES) {
            call.respond(
                HttpStatusCode.PayloadTooLarge,
                ImageUploadErrorResponse("fileTooLarge")
            )
            return@post
        }

        val file = resourceManager.saveTempFile(
            IMAGE_UPLOAD_BASE,
            fileBytes,
            ResourceType.UPLOAD,
            extension
        )

        val uploadFilePath =
            "$TEMP_URL${resourceManager.constructTempUrlFromPath(file)}"

        call.respond(
            HttpStatusCode.OK,
            ImageUploadResponse(ImageUploadFilePath(uploadFilePath))
        )
    }


    route("/entry/{entryId}/resource") {

        val cacheExpiresAge = LocalDate.now().plusYears(5).atStartOfDay()
            .with(TemporalAdjusters.firstDayOfYear())
        // used when retrieving resource files to prevent lookups
        val resourceCache = ConcurrentHashMap<ResourceId, Pair<Resource, File>>()

        get {
            val id = EntryId(call.parameters["entryId"] ?: throw InvalidModelException("Missing entryId"))
            call.respond(resourceManager.getResourcesFor(id))
        }

        get("/{id}/info") {
            val id = ResourceId(call.parameters["id"] ?: throw InvalidModelException("Missing id"))
            val resource = resourceManager.getResource(id)
            if (resource == null) call.respond(HttpStatusCode.NotFound)
            else {
                call.response.header("X-Resource-Mime-Type", deriveMimeType(resource.name))
                call.respond(resource)
            }
        }

        get("/{id}") {
            val id = ResourceId(call.parameters["id"] ?: throw InvalidModelException("Missing id"))
            val res = resourceCache[id] ?: resourceManager.getResourceAsFile(id)?.also {
                resourceCache[id] = it
            }
            if (res != null) {
                call.response.header(HttpHeaders.ContentDisposition, "inline; filename=\"${res.first.name}\"")
                call.response.header(HttpHeaders.Expires, cacheExpiresAge)
                call.response.header(HttpHeaders.ETag, HashUtils.sha1Hash(res.first.dateCreated.toString()))
                call.respondFile(res.second)
            } else call.respond(HttpStatusCode.NotFound)
        }

        post {
            val entryId = EntryId(call.parameters["entryId"] ?: throw InvalidModelException("Missing entryId"))
            val multipart = call.receiveMultipart()
            var res: Resource? = null
            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    val name = part.originalFileName ?: throw InvalidModelException("Missing fileName")
                    res = resourceManager.saveUploadedResource(entryId, name, part.provider().toInputStream())
                }
                part.dispose()
            }
            if (res == null) throw InvalidModelException()
            else call.respond(HttpStatusCode.Created, res)
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
            val id = ResourceId(call.parameters["id"] ?: throw InvalidModelException("Missing id"))
            val removed = resourceManager.delete(id)
            if (removed) {
                resourceCache.remove(id)
                call.respond(HttpStatusCode.OK)
            }
            else call.respond(HttpStatusCode.NotFound)
        }

    }
}
