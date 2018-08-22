package notify

import com.google.common.collect.Sets
import common.Environment
import io.ktor.http.cio.websocket.Frame
import kotlinx.coroutines.experimental.channels.SendChannel
import org.apache.commons.mail.HtmlEmail
import util.JsonMapper.defaultMapper
import util.loggerFor

private val logger = loggerFor<NotifyService>()

class NotifyService {

    private val notifiers = Sets.newConcurrentHashSet<SendChannel<Frame>>()

    suspend fun accept(notify: Notification, body: Any?) {
        logger.info("Accepting ${notify.type} notification: ${notify.message}")
        notifiers.forEach {
            if(it.isClosedForSend) notifiers.remove(it)
            else {
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
        if(!Environment.mail.enabled) return

        val toMail = "user@email.com"
        val email = HtmlEmail()
        email.hostName = Environment.mail.server
        email.setSmtpPort(Environment.mail.port)
        email.setFrom("noreply@lynks.com")
        email.addTo(toMail)
        email.subject = subject
        email.setHtmlMsg(body)
        email.send()
    }

}