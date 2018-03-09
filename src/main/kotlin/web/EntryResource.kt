package web

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import service.EntryService
import util.pageRequest

fun Route.entry(entryService: EntryService) {

    route("/entry") {

        get("/") {
            call.respond(entryService.get(call.pageRequest()))
        }

        get("/{id}") {
            val entry = entryService.get(call.parameters["id"]!!)
            if (entry == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(entry)
        }
    }
}
