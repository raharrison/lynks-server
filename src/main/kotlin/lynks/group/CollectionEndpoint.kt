package lynks.group

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*

fun Route.collection(collectionService: CollectionService) {

    route("/collection") {

        get {
            call.respond(collectionService.getAll())
        }

        get("/{id}") {
            val collection = collectionService.get(call.parameters["id"]!!)
            if (collection == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(collection)
        }

        post {
            val collection = call.receive<NewCollection>()
            call.respond(HttpStatusCode.Created, collectionService.add(collection))
        }

        put {
            val collection = call.receive<NewCollection>()
            val updated = collectionService.update(collection)
            if (updated == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val id = call.parameters["id"]!!
            val removed = collectionService.delete(id)
            if (removed) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.NotFound)
        }

        post("/refresh") {
            collectionService.rebuild()
            call.respond(HttpStatusCode.OK)
        }

    }
}
