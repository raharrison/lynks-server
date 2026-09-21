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
import lynks.common.exception.NotFoundException
import lynks.util.FileUtils
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
        var extension: String? = null

        multipart.forEachPart { part ->
            try {
                if (part is PartData.FileItem) {
                    // Derive extension from content type first, fall back to filename
                    val ctExt = when (part.contentType?.contentSubtype?.lowercase()) {
                        "jpeg", "jpg" -> "jpg"
                        "png"         -> "png"
                        "gif"         -> "gif"
                        "webp"        -> "webp"
                        else          -> null
                    }
                    val nameExt = part.originalFileName
                        ?.let { FileUtils.getExtension(it).lowercase() }
                        ?.let { if (it == "jpeg") "jpg" else it }
                    extension = ctExt ?: nameExt

                    if (extension in ALLOWED_IMAGE_EXTENSIONS) {
                        // Read at most MAX+1 bytes - if result exceeds MAX we reject below
                        fileBytes = part.provider()
                            .readBuffer(MAX_IMAGE_UPLOAD_BYTES.toLong() + 1)
                            .readByteArray()
                    }
                }
            } finally {
                part.release()
            }
        }

        val ext = extension ?: run {
            call.respond(HttpStatusCode.BadRequest, ImageUploadErrorResponse("noFileGiven"))
            return@post
        }

        if (ext !in ALLOWED_IMAGE_EXTENSIONS) {
            call.respond(HttpStatusCode.UnsupportedMediaType, ImageUploadErrorResponse("typeNotAllowed"))
            return@post
        }

        val bytes = fileBytes ?: run {
            call.respond(HttpStatusCode.BadRequest, ImageUploadErrorResponse("noFileGiven"))
            return@post
        }

        if (bytes.size > MAX_IMAGE_UPLOAD_BYTES) {
            call.respond(HttpStatusCode.PayloadTooLarge, ImageUploadErrorResponse("fileTooLarge"))
            return@post
        }

        val file = resourceManager.saveTempFile(IMAGE_UPLOAD_BASE, bytes, ResourceType.UPLOAD, ext)
        val uploadFilePath = "$TEMP_URL${resourceManager.constructTempUrlFromPath(file)}"
        call.respond(HttpStatusCode.OK, ImageUploadResponse(ImageUploadFilePath(uploadFilePath)))
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
            val resource = resourceManager.getResource(id) ?: throw NotFoundException()
            call.response.header("X-Resource-Mime-Type", deriveMimeType(resource.name))
            call.respond(resource)
        }

        get("/{id}") {
            val id = ResourceId(call.parameters["id"] ?: throw InvalidModelException("Missing id"))
            val res = resourceCache[id] ?: resourceManager.getResourceAsFile(id)?.also {
                resourceCache.putIfAbsent(id, it)
            }
            if (res == null) throw NotFoundException()
            call.response.header(HttpHeaders.ContentDisposition, "inline; filename=\"${res.first.name}\"")
            call.response.header(HttpHeaders.Expires, cacheExpiresAge)
            call.response.header(HttpHeaders.ETag, res.first.dateCreated.toString())
            call.respondFile(res.second)
        }

        post {
            val entryId = EntryId(call.parameters["entryId"] ?: throw InvalidModelException("Missing entryId"))
            val multipart = call.receiveMultipart()
            var res: Resource? = null
            multipart.forEachPart { part ->
                try {
                    if (part is PartData.FileItem) {
                        val name = part.originalFileName ?: throw InvalidModelException("Missing fileName")
                        part.provider().toInputStream().use { input ->
                            res = resourceManager.saveUploadedResource(entryId, name, input)
                        }
                    }
                } finally {
                    part.release()
                }
            }
            if (res == null) throw InvalidModelException()
            else call.respond(HttpStatusCode.Created, res)
        }

        put {
            val resource = call.receive<Resource>()
            val updated = resourceManager.updateResource(resource) ?: throw NotFoundException()
            resourceCache.remove(updated.id)
            call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val id = ResourceId(call.parameters["id"] ?: throw InvalidModelException("Missing id"))
            if (!resourceManager.delete(id)) throw NotFoundException()
            resourceCache.remove(id)
            call.respond(HttpStatusCode.OK)
        }

    }
}
