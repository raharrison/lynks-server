package lynks.common.endpoint

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*

fun Route.health() {

    val ok = mapOf("status" to "ok")

    get("/health") {
        call.respond(ok)
    }

}
