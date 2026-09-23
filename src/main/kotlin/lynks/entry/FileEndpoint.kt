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
import lynks.util.userId
import lynks.util.versionParameter

fun Route.file(fileService: FileService) {

    route("/file") {

        get {
            call.respond(fileService.get(call.userId(), call.pageRequest()))
        }

        get("/{id}") {
            val file = fileService.get(call.userId(), EntryId(call.parameters["id"] ?: throw InvalidModelException("Missing id")))
                ?: throw NotFoundException()
            call.respond(file)
        }

        get("/{id}/{version}") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            val file = fileService.get(call.userId(), EntryId(id), call.versionParameter()) ?: throw NotFoundException()
            call.respond(file)
        }

        post("/{id}/revert/{version}") {
            val id = EntryId(call.parameters["id"] ?: throw InvalidModelException("Missing id"))
            val reverted = fileService.revert(call.userId(), id, call.versionParameter()) ?: throw NotFoundException()
            call.respond(HttpStatusCode.OK, reverted)
        }

        post {
            val file = call.receive<NewFile>()
            call.respond(HttpStatusCode.Created, fileService.add(call.userId(), file))
        }

        put {
            val file = call.receive<NewFile>()
            val newVersion = call.parameters["newVersion"]?.let { it.toBoolean() } ?: true
            val updated = fileService.update(call.userId(), file, newVersion) ?: throw NotFoundException()
            call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            if (!fileService.delete(
                    call.userId(),
                    EntryId(call.parameters["id"] ?: throw InvalidModelException("Missing id"))
                )
            ) throw NotFoundException()
            call.respond(HttpStatusCode.OK)
        }

    }
}
