package entry

import common.NewFact
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import util.pageRequest

fun Route.fact(factService: FactService) {

    route("/fact") {

        get {
            call.respond(factService.get(call.pageRequest()))
        }

        get("/{id}") {
            val fact = factService.get(call.parameters["id"]!!)
            if (fact == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(fact)
        }

        get("/{id}/{version}") {
            val id = call.parameters["id"]!!
            val version = call.parameters["version"]!!
            val fact = factService.get(id, version.toInt())
            if (fact == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(fact)
        }

        post {
            val fact = call.receive<NewFact>()
            call.respond(HttpStatusCode.Created, factService.add(fact))
        }

        put {
            val fact = call.receive<NewFact>()
            val newVersion = call.parameters["newVersion"]?.let { it.toBoolean() } ?: true
            val updated = factService.update(fact, newVersion)
            if (updated == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val removed = factService.delete(call.parameters["id"]!!)
            if (removed) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.NotFound)
        }

    }
}
