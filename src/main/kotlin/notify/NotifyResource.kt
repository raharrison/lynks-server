package notify

import io.ktor.routing.Route
import io.ktor.websocket.webSocket

fun Route.notify(notifyService: NotifyService) {

    webSocket("/notify") {
        try {
            notifyService.join(this.outgoing)
            while(true) {
                incoming.receive()
            }
        } finally {
            notifyService.leave(this.outgoing)
        }
    }

}