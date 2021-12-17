package lynks.notify

import io.ktor.routing.*
import io.ktor.websocket.*

fun Route.notify(notifyService: NotifyService) {

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
