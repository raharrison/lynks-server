package suggest

import io.ktor.application.call
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get

fun Route.suggest(suggestionService: SuggestionService) {

    get("/suggest") {
        val url = call.parameters["url"]
        if (url == null) call.respond("url parameter required")
        else call.respond(suggestionService.processLink(url))
    }

}
