package suggest

import common.exception.InvalidModelException
import io.ktor.application.call
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import util.URLUtils

fun Route.suggest(suggestionService: SuggestionService) {

    post("/suggest") {
        val url = call.receive<String>()
        if(!URLUtils.isValidUrl(url)) throw InvalidModelException("Invalid URL")
        else call.respond(suggestionService.processLink(url))
    }

}
