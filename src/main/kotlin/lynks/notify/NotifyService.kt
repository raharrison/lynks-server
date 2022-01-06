package lynks.notify

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import lynks.common.Environment
import lynks.user.UserService
import lynks.util.JsonMapper.defaultMapper
import lynks.util.loggerFor
import org.apache.commons.mail.HtmlEmail
import java.util.concurrent.ConcurrentHashMap

private val log = loggerFor<NotifyService>()

class NotifyService(private val userService: UserService) {

    private val notifiers = ConcurrentHashMap.newKeySet<SendChannel<Frame>>()

    @ExperimentalCoroutinesApi
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
