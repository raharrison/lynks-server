package lynks.notify

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import lynks.common.NotificationId
import lynks.common.exception.InvalidModelException
import lynks.common.exception.NotFoundException
import lynks.util.pageRequest
import lynks.util.userId

fun Route.notify(notifyService: NotifyService) {

    route("/notifications") {

        get {
            val page = call.pageRequest()
            call.respond(notifyService.getNotifications(call.userId(), page))
        }

        get("/{id}") {
            val notificationId = NotificationId(call.parameters["id"] ?: throw InvalidModelException("Missing id"))
            val notification = notifyService.getNotification(call.userId(), notificationId) ?: throw NotFoundException()
            call.respond(notification)
        }

        get("/unread") {
            val unreadCount = notifyService.getUnreadCount(call.userId())
            val response = mapOf("unread" to unreadCount)
            call.respond(HttpStatusCode.OK, response)
        }

        post("/{id}/read") {
            val notificationId = NotificationId(call.parameters["id"] ?: throw InvalidModelException("Missing id"))
            if (notifyService.read(call.userId(), notificationId, true) == 0) throw NotFoundException()
            call.respond(HttpStatusCode.OK)
        }

        post("/{id}/unread") {
            val notificationId = NotificationId(call.parameters["id"] ?: throw InvalidModelException("Missing id"))
            if (notifyService.read(call.userId(), notificationId, false) == 0) throw NotFoundException()
            call.respond(HttpStatusCode.OK)
        }

        post("/markAllRead") {
            val markedRead = notifyService.markAllRead(call.userId())
            val response = mapOf("read" to markedRead)
            call.respond(HttpStatusCode.OK, response)
        }

    }
}
