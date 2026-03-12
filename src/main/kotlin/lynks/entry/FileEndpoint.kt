package lynks.entry

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import lynks.common.EntryId
import lynks.common.NewFile
import lynks.common.exception.InvalidModelException
import lynks.common.exception.NotFoundException
import lynks.util.pageRequest

fun Route.file(fileService: FileService) {

    route("/file") {

        get {
            call.respond(fileService.get(call.pageRequest()))
        }

        get("/{id}") {
            val file = fileService.get(EntryId(call.parameters["id"] ?: throw InvalidModelException("Missing id"))) ?: throw NotFoundException()
            call.respond(file)
        }

        get("/{id}/{version}") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            val version = call.parameters["version"] ?: throw InvalidModelException("Missing version")
            val file = fileService.get(EntryId(id), version.toInt()) ?: throw NotFoundException()
            call.respond(file)
        }

        post {
            val file = call.receive<NewFile>()
            call.respond(HttpStatusCode.Created, fileService.add(file))
        }

        put {
            val file = call.receive<NewFile>()
            val newVersion = call.parameters["newVersion"]?.let { it.toBoolean() } ?: true
            val updated = fileService.update(file, newVersion) ?: throw NotFoundException()
            call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            if (!fileService.delete(EntryId(call.parameters["id"] ?: throw InvalidModelException("Missing id")))) throw NotFoundException()
            call.respond(HttpStatusCode.OK)
        }

    }
}
