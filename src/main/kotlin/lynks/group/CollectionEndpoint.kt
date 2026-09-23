package lynks.group

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import lynks.common.exception.InvalidModelException
import lynks.common.exception.NotFoundException
import lynks.util.userId

fun Route.collection(collectionService: CollectionService) {

    route("/collection") {

        get {
            call.respond(collectionService.getAll(call.userId()))
        }

        get("/{id}") {
            val collection =
                collectionService.get(call.userId(), call.parameters["id"] ?: throw InvalidModelException("Missing id"))
                    ?: throw NotFoundException()
            call.respond(collection)
        }

        post {
            val collection = call.receive<NewCollection>()
            call.respond(HttpStatusCode.Created, collectionService.add(call.userId(), collection))
        }

        put {
            val collection = call.receive<NewCollection>()
            val updated = collectionService.update(call.userId(), collection) ?: throw NotFoundException()
            call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val id = call.parameters["id"] ?: throw InvalidModelException("Missing id")
            if (!collectionService.delete(call.userId(), id)) throw NotFoundException()
            call.respond(HttpStatusCode.OK)
        }

        post("/refresh") {
            collectionService.rebuild(call.userId())
            call.respond(HttpStatusCode.OK)
        }

    }
}
