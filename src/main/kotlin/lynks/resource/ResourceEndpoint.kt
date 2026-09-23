package lynks.resource

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
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
import lynks.util.userId
import java.io.File
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.ConcurrentHashMap

fun Route.resource(resourceManager: ResourceManager) {

    // pasted images are served by the route below, which only looks in the caller's own directory
    val tempUploadDir = resourceManager.tempUploadBaseDir().toFile()
    staticFiles("temp", File(Environment.resource.resourceTempPath)) {
        exclude { file -> file.absoluteFile.normalize().startsWith(tempUploadDir) }
        modify { file, call -> call.sandboxUnlessPdf(file.extension) }
    }

    get("temp/$TEMP_UPLOAD_DIR/{name}") {
        val name = call.parameters["name"] ?: throw InvalidModelException("Missing name")
        val file = resourceManager.findTempUpload(call.userId(), name)?.toFile() ?: throw NotFoundException()
        call.sandboxUnlessPdf(file.extension)
        call.respondFile(file)
    }

    // static exclusion answers 403, which would confirm a file exists in another user's directory
    get("temp/$TEMP_UPLOAD_DIR/{path...}") {
        throw NotFoundException()
    }

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

        val file = resourceManager.saveTempUpload(call.userId(), bytes, ext)
        call.respond(HttpStatusCode.OK, ImageUploadResponse(ImageUploadFilePath("$TEMP_UPLOAD_URL${file.fileName}")))
    }


    route("/entry/{entryId}/resource") {

        val cacheExpiresAge = LocalDate.now().plusYears(5).atStartOfDay()
            .with(TemporalAdjusters.firstDayOfYear())
        // used when retrieving resource files to prevent lookups, keyed by owner so a hit never crosses users
        val resourceCache = ConcurrentHashMap<Pair<UserId, ResourceId>, Pair<Resource, File>>()

        get {
            val id = EntryId(call.parameters["entryId"] ?: throw InvalidModelException("Missing entryId"))
            call.respond(resourceManager.getResourcesFor(call.userId(), id))
        }

        get("/{id}/info") {
            val entryId = EntryId(call.parameters["entryId"] ?: throw InvalidModelException("Missing entryId"))
            val id = ResourceId(call.parameters["id"] ?: throw InvalidModelException("Missing id"))
            val resource = resourceManager.getResource(call.userId(), entryId, id) ?: throw NotFoundException()
            call.response.header("X-Resource-Mime-Type", deriveMimeType(resource.name))
            call.respond(resource)
        }

        get("/{id}") {
            val userId = call.userId()
            val entryId = EntryId(call.parameters["entryId"] ?: throw InvalidModelException("Missing entryId"))
            val id = ResourceId(call.parameters["id"] ?: throw InvalidModelException("Missing id"))
            val key = userId to id
            // a deleted entry removes its files after commit, which the cache would otherwise outlive
            val cached = resourceCache[key]?.takeIf { it.first.entryId == entryId && it.second.exists() }
            val res = cached ?: resourceManager.getResourceAsFile(userId, entryId, id)?.also {
                resourceCache[key] = it
            }
            if (res == null) {
                resourceCache.remove(key)
                throw NotFoundException()
            }
            call.response.header(HttpHeaders.ContentDisposition, "inline; filename=\"${res.first.name}\"")
            call.response.header(HttpHeaders.Expires, cacheExpiresAge)
            call.response.header(HttpHeaders.ETag, res.first.dateCreated.toString())
            call.sandboxUnlessPdf(res.first.extension)
            call.respondFile(res.second)
        }

        post {
            val userId = call.userId()
            val entryId = EntryId(call.parameters["entryId"] ?: throw InvalidModelException("Missing entryId"))
            val multipart = call.receiveMultipart()
            var res: Resource? = null
            multipart.forEachPart { part ->
                try {
                    if (part is PartData.FileItem) {
                        val name = part.originalFileName ?: throw InvalidModelException("Missing fileName")
                        part.provider().toInputStream().use { input ->
                            res = resourceManager.saveUploadedResource(userId, entryId, name, input)
                                ?: throw NotFoundException()
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
            val userId = call.userId()
            val entryId = EntryId(call.parameters["entryId"] ?: throw InvalidModelException("Missing entryId"))
            val resource = call.receive<Resource>()
            val updated = resourceManager.updateResource(userId, entryId, resource) ?: throw NotFoundException()
            resourceCache.remove(userId to updated.id)
            call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val userId = call.userId()
            val entryId = EntryId(call.parameters["entryId"] ?: throw InvalidModelException("Missing entryId"))
            val id = ResourceId(call.parameters["id"] ?: throw InvalidModelException("Missing id"))
            if (!resourceManager.delete(userId, entryId, id)) throw NotFoundException()
            resourceCache.remove(userId to id)
            call.respond(HttpStatusCode.OK)
        }

    }
}

// Scraped pages and uploads are untrusted but served from the app's own origin. PDFs are left out
// because browsers refuse to run their PDF viewer inside a sandbox.
private fun ApplicationCall.sandboxUnlessPdf(extension: String) {
    if (!extension.equals("pdf", ignoreCase = true)) {
        response.header("Content-Security-Policy", "sandbox")
    }
}
