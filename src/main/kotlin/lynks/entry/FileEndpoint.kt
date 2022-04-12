package lynks.entry

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import lynks.common.NewFile
import lynks.util.pageRequest

fun Route.file(fileService: FileService) {

    route("/file") {

        get {
            call.respond(fileService.get(call.pageRequest()))
        }

        get("/{id}") {
            val file = fileService.get(call.parameters["id"]!!)
            if (file == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(file)
        }

        get("/{id}/{version}") {
            val id = call.parameters["id"]!!
            val version = call.parameters["version"]!!
            val file = fileService.get(id, version.toInt())
            if (file == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(file)
        }

        post {
            val file = call.receive<NewFile>()
            call.respond(HttpStatusCode.Created, fileService.add(file))
        }

        put {
            val file = call.receive<NewFile>()
            val newVersion = call.parameters["newVersion"]?.let { it.toBoolean() } ?: true
            val updated = fileService.update(file, newVersion)
            if (updated == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val removed = fileService.delete(call.parameters["id"]!!)
            if (removed) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.NotFound)
        }

    }
}
