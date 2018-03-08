package web

import io.ktor.application.call
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import model.Suggestion
import service.SuggestionService

fun Route.suggest(suggestionService: SuggestionService) {

    post("/suggest") {
        val suggestion = call.receive<Suggestion>()
        call.respond(suggestionService.suggest(suggestion))
    }

}
