package lynks.digest

import io.ktor.server.response.*
import io.ktor.server.routing.*
import lynks.common.exception.NotFoundException
import lynks.util.userId

fun Route.digest(digestService: DigestService) {

    route("/digest") {

        get {
            call.respond(digestService.getLatest(call.userId()) ?: throw NotFoundException("No digest generated yet"))
        }

    }

}
