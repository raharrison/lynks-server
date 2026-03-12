package lynks.group

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import lynks.common.exception.InvalidModelException
import lynks.common.exception.NotFoundException

fun Route.collection(collectionService: CollectionService) {

    route("/collection") {

        get {
            call.respond(collectionService.getAll())
        }

        get("/{id}") {
            val collection = collectionService.get(call.parameters["id"] ?: throw InvalidModelException("Missing id")) ?: throw NotFoundException()
            call.respond(collection)
        }

        post {
            val collection = call.receive<NewCollection>()
            call.respond(HttpStatusCode.Created, collectionService.add(collection))
        }

        put {
            val collection = call.receive<NewCollection>()
            val updated = collectionService.update(collection) ?: throw NotFoundException()
            call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            if (!collectionService.delete(id)) throw NotFoundException()
            call.respond(HttpStatusCode.OK)
        }

        post("/refresh") {
            collectionService.rebuild()
            call.respond(HttpStatusCode.OK)
        }

    }
}
