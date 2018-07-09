package notify

import com.google.common.collect.Sets
import io.ktor.http.cio.websocket.Frame
import kotlinx.coroutines.experimental.channels.SendChannel
import util.loggerFor

private val logger = loggerFor<NotifyService>()

class NotifyService {

    private val notifiers = Sets.newConcurrentHashSet<SendChannel<Frame>>()

    suspend fun accept(any: Any) {
        logger.info("Accepted notification for: $any")
        notifiers.forEach {
            if(it.isClosedForSend) notifiers.remove(it)
            else it.send(Frame.Text(any.toString()))
        }
    }

    fun join(outgoing: SendChannel<Frame>) {
        notifiers += outgoing
    }

    fun leave(outgoing: SendChannel<Frame>) {
        notifiers -= outgoing
    }

}