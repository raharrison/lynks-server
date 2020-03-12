package notify

import io.ktor.http.cio.websocket.Frame
import io.ktor.routing.Route
import io.ktor.websocket.webSocket
import kotlinx.coroutines.delay

fun Route.notify(notifyService: NotifyService) {

    webSocket("/notify") {
        try {
            notifyService.join(this.outgoing)
            var i = 0
            while(true) {
//                incoming.receive()
                this.outgoing.send(Frame.Text("{\"something\": $i}"))
                i++
                delay(1000)
            }
        } finally {
            notifyService.leave(this.outgoing)
        }
    }

}