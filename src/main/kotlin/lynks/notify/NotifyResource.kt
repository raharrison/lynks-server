package lynks.notify

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.websocket.*
import lynks.util.pageRequest

fun Route.notify(notifyService: NotifyService) {

    route("/notifications") {

        get {
            val page = call.pageRequest()
            call.respond(notifyService.getNotifications(page))
        }

        get("/{id}") {
            val notificationId = call.parameters["id"]!!
            val notification = notifyService.getNotification(notificationId)
            if (notification == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(notification)
        }

        post("/{id}/read") {
            val notificationId = call.parameters["id"]!!
            val updated = notifyService.read(notificationId, true)
            if (updated == 0) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.OK)
        }

        post("/{id}/unread") {
            val notificationId = call.parameters["id"]!!
            val updated = notifyService.read(notificationId, false)
            if (updated == 0) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.OK)
        }

    }

    webSocket("/notify") {
        try {
            notifyService.join(this.outgoing)
            while (true) {
                incoming.receive()
            }
        } finally {
            notifyService.leave(this.outgoing)
        }
    }

}
