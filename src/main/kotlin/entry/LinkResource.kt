package entry

import common.NewLink
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.*
import util.pageRequest

fun Route.link(linkService: LinkService) {

    route("/link") {

        get("/") {
            call.respond(linkService.get(call.pageRequest()))
        }

        get("/{id}") {
            val link = linkService.get(call.parameters["id"]!!)
            if (link == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(link)
        }

        post("/") {
            val link = call.receive<NewLink>()
            call.respond(linkService.add(link))
        }

        put("/") {
            val link = call.receive<NewLink>()
            call.respond(linkService.update(link))
        }

        delete("/{id}") {
            val removed = linkService.delete(call.parameters["id"]!!)
            if (removed) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.NotFound)
        }

        get("/{id}/screenshot") {
            val screenshot = linkService.generateScreenshotAsync(call.parameters["id"]!!).await()
            if (screenshot == null) call.respond(HttpStatusCode.BadRequest)
            else call.respond(screenshot)
        }
    }
}
