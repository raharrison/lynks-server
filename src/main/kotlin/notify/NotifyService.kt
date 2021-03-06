package notify

import common.Environment
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.channels.SendChannel
import org.apache.commons.mail.HtmlEmail
import user.UserService
import util.JsonMapper.defaultMapper
import util.loggerFor
import java.util.concurrent.ConcurrentHashMap

private val log = loggerFor<NotifyService>()

class NotifyService(private val userService: UserService) {

    private val notifiers = ConcurrentHashMap.newKeySet<SendChannel<Frame>>()

    suspend fun accept(notify: Notification, body: Any?) {
        log.info("Accepting ${notify.type} notification: ${notify.message}")
        notifiers.forEach {
            if (it.isClosedForSend) {
                log.warn("Notifier is closed for sending, removing from pool")
                notifiers.remove(it)
            } else {
                val payload = defaultMapper.writeValueAsString(buildNotification(notify, body))
                it.send(Frame.Text(payload))
            }
        }
    }

    private fun buildNotification(notify: Notification, body: Any?): Map<String, Any?> {
        val entityType = body?.javaClass?.simpleName
        return mapOf("entity" to entityType,
                "type" to notify.type,
                "message" to notify.message,
                "body" to body)

    }

    fun join(outgoing: SendChannel<Frame>) {
        notifiers += outgoing
    }

    fun leave(outgoing: SendChannel<Frame>) {
        notifiers -= outgoing
    }

    fun sendEmail(subject: String, body: String) {
        if (!Environment.mail.enabled) return
        val address = userService.currentUserPreferences.email
        address?.let {
            log.info("Sending notification email subject={}", subject)
            val email = HtmlEmail()
            email.hostName = Environment.mail.server
            email.setSmtpPort(Environment.mail.port)
            email.setFrom("noreply@lynks.com")
            email.addTo(it)
            email.subject = subject
            email.setHtmlMsg(body)
            email.send()
        }
    }

}
