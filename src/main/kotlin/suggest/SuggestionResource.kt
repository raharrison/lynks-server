package suggest

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import util.URLUtils

fun Route.suggest(suggestionService: SuggestionService) {

    post("/suggest") {
        val url = call.receive<String>()
        if(!URLUtils.isValidUrl(url)) call.respond(HttpStatusCode.BadRequest, "Invalid url provided")
        call.respond(suggestionService.processLink(url))
    }

}
