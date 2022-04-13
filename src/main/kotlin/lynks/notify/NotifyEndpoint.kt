package lynks.notify

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
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

        get("/unread") {
            val unreadCount = notifyService.getUnreadCount()
            val response = mapOf("unread" to unreadCount)
            call.respond(HttpStatusCode.OK, response)
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

        post("/markAllRead") {
            val markedRead = notifyService.markAllRead()
            val response = mapOf("read" to markedRead)
            call.respond(HttpStatusCode.OK, response)
        }

        webSocket("/updates") {
            try {
                call.application.log.info("New notification socket listener joining")
                notifyService.join(this.outgoing)
                for (frame in incoming) {
                    if (frame.frameType == FrameType.CLOSE) {
                        break
                    }
                }
            } finally {
                call.application.log.info("Notification socket listener leaving")
                notifyService.leave(this.outgoing)
            }
        }

    }
}
