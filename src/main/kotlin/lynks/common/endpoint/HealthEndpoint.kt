package lynks.common.endpoint

import io.ktor.server.response.*
import io.ktor.server.routing.*
import lynks.common.BuildInfo

fun Route.health() {

    get("/health") {
        call.respond(mapOf("status" to "up", "application" to "lynks", "version" to BuildInfo.version))
    }

}
