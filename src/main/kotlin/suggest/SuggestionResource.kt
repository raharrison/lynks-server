package suggest

import io.ktor.application.call
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post

fun Route.suggest(suggestionService: SuggestionService) {

    post("/suggest") {
        val url = call.receive<String>()
        call.respond(suggestionService.processLink(url))
    }

}
