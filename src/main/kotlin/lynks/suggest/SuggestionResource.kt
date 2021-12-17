package lynks.suggest

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import lynks.common.exception.InvalidModelException
import lynks.util.URLUtils

fun Route.suggest(suggestionService: SuggestionService) {

    post("/suggest") {
        val url = call.receive<String>()
        if(!URLUtils.isValidUrl(url)) throw InvalidModelException("Invalid URL")
        else call.respond(suggestionService.processLink(url))
    }

}
