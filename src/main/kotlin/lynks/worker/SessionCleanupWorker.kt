package lynks.worker

import kotlinx.coroutines.time.delay
import lynks.auth.SessionService
import java.time.Duration

class SessionCleanupWorkerRequest(val interval: Duration) {
    override fun hashCode(): Int = 1
    override fun equals(other: Any?): Boolean = other is SessionCleanupWorkerRequest
}

class SessionCleanupWorker(private val sessionService: SessionService) : ChannelBasedWorker<SessionCleanupWorkerRequest>() {

    override suspend fun beforeWork() {
        super.onChannelReceive(SessionCleanupWorkerRequest(Duration.ofDays(1)))
    }

    override suspend fun doWork(input: SessionCleanupWorkerRequest) {
        while (true) {
            try {
                val deleted = sessionService.deleteExpired()
                log.info("Session cleanup worker removed {} expired sessions", deleted)
            } catch (e: Exception) {
                log.error("Session cleanup worker failed", e)
            }
            delay(input.interval)
        }
    }

}
