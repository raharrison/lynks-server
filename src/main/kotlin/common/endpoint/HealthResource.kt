package common.endpoint

import io.ktor.application.call
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get

fun Route.health() {

    val ok = mapOf("status" to "ok")

    get("/health") {
        call.respond(ok)
    }

}