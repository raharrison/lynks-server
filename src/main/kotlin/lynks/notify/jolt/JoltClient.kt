package lynks.notify.jolt

import lynks.common.Environment
import lynks.resource.WebResourceRetriever
import lynks.util.Result
import lynks.util.loggerFor

class JoltClient(private val webResourceRetriever: WebResourceRetriever) {

    private val log = loggerFor<JoltClient>()

    suspend fun sendNotification(title: String?, message: String, click: String? = null) {
        val host = Environment.external.joltHost
        val token = Environment.external.joltToken
        if (host == null || token == null) {
            log.warn("Jolt host or channel token not set, unable to send notification")
            return
        }

        log.info("Sending jolt notification message={}", message)
        val payload = buildMap {
            put("message", message)
            if (title != null) put("title", "Lynks - $title")
            if (click != null) put("click", click)
        }

        val response = webResourceRetriever.postStringResult("$host/inbound/$token", payload)
        if (response is Result.Failure) {
            throw response.reason
        }
        log.info("Jolt notification sent successfully")
    }

}
