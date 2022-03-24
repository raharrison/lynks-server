package lynks.notify.pushover

import lynks.common.Environment
import lynks.resource.WebResourceRetriever
import lynks.util.Result
import lynks.util.loggerFor

class PushoverClient(private val webResourceRetriever: WebResourceRetriever) {

    private val pushoverMessageUrl = "https://api.pushover.net/1/messages.json"
    private val log = loggerFor<PushoverClient>()

    suspend fun sendNotification(title: String?, message: String) {
        val pushoverToken = Environment.external.pushoverToken
        val pushoverUser = Environment.external.pushoverUser
        if (pushoverToken == null || pushoverUser == null) {
            log.warn("Pushover token or user key not set, unable to send notification")
            return
        }

        log.info("Sending pushover notification message={}", message)
        val params = mutableMapOf(
            "token" to pushoverToken,
            "user" to pushoverUser,
            "message" to message
        )
        if (title != null) {
            params["title"] = "Lynks - $title"
        }

        val response = webResourceRetriever.postFormStringResult(pushoverMessageUrl, params)
        if (response is Result.Failure) {
            throw response.reason
        } else {
            log.info("Pushover notification sent successfully")
        }

    }

}
