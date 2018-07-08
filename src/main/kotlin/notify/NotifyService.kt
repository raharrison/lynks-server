package notify

import util.loggerFor

private val logger = loggerFor<NotifyService>()

class NotifyService {

    fun accept(any: Any) {
        logger.info("Accepted notification for: $any")
    }

}