package tag

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.*

fun Route.collection(collectionService: CollectionService) {

    route("/collection") {

        get("/") {
            call.respond(collectionService.getAll())
        }

        get("/{id}") {
            val collection = collectionService.get(call.parameters["id"]!!)
            if (collection == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(collection)
        }

        post("/") {
            val collection = call.receive<NewCollection>()
            call.respond(HttpStatusCode.Created, collectionService.add(collection))
        }

        put("/") {
            val collection = call.receive<NewCollection>()
            val updated = collectionService.update(collection)
            if(updated == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val id = call.parameters["id"]!!
            val removed = collectionService.delete(id)
            if (removed) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.NotFound)
        }
    }
}