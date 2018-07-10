package notify

import com.google.common.collect.Sets
import io.ktor.http.cio.websocket.Frame
import kotlinx.coroutines.experimental.channels.SendChannel
import util.JsonMapper.defaultMapper
import util.loggerFor

private val logger = loggerFor<NotifyService>()

enum class Notification { CREATE, UPDATE, DELETE, EXECUTED, ERROR }

class NotifyService {

    private val notifiers = Sets.newConcurrentHashSet<SendChannel<Frame>>()

    suspend fun accept(type: Notification, any: Any) {
        logger.info("Accepted notification for: $any")
        notifiers.forEach {
            if(it.isClosedForSend) notifiers.remove(it)
            else {
                val payload = defaultMapper.writeValueAsString(buildNotification(type, any))
                it.send(Frame.Text(defaultMapper.writeValueAsString(payload)))
            }
        }
    }

    private fun buildNotification(type: Notification, any: Any): Map<String, Any?> {
        val entityType = any::class.simpleName
        return mapOf("entity" to entityType,
                "type" to type,
                "body" to any)

    }

    fun join(outgoing: SendChannel<Frame>) {
        notifiers += outgoing
    }

    fun leave(outgoing: SendChannel<Frame>) {
        notifiers -= outgoing
    }

}