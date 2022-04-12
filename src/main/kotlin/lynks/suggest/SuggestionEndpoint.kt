package lynks.suggest

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import lynks.common.exception.InvalidModelException
import lynks.util.URLUtils

fun Route.suggest(suggestionService: SuggestionService) {

    post("/suggest") {
        val url = call.receive<String>()
        if(!URLUtils.isValidUrl(url)) throw InvalidModelException("Invalid URL")
        else call.respond(suggestionService.processLink(url))
    }

}
