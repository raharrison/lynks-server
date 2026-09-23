package lynks.worker

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.time.delay
import lynks.digest.DigestService
import lynks.notify.NewNotification
import lynks.notify.NotifyService
import lynks.user.UserService
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

class UnreadLinkDigestWorker(
    private val notifyService: NotifyService,
    private val digestService: DigestService,
    private val userService: UserService
) : ChannelBasedWorker<String>() {

    override suspend fun beforeWork() {
        super.onChannelReceive("start")
    }

    override suspend fun doWork(input: String) {
        while (true) {
            val wait = untilNextFire(LocalDateTime.now())
            log.debug("Link digest worker sleeping for {} hours until next fire", wait.toHours())
            delay(wait)
            regenerate()
        }
    }

    internal fun untilNextFire(now: LocalDateTime): Duration {
        val thisWeek = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).truncatedTo(ChronoUnit.DAYS).withHour(9)
        val fire = if (thisWeek.isAfter(now)) thisWeek else thisWeek.plusWeeks(1)
        return Duration.between(now, fire)
    }

    private suspend fun regenerate() {
        for (user in userService.getActiveUsers()) {
            try {
                val digest = digestService.generate(user.id)
                if (digest == null) {
                    log.info("Link digest worker found no unread links, no digest generated user={}", user.id)
                } else if (!user.digest) {
                    log.info("Digest regenerated but digest notifications are disabled user={}", user.id)
                } else {
                    notifyService.create(
                        user.id,
                        NewNotification.digest("Weekly digest ready with ${digest.links.size} unread links")
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Link digest worker failed user={}", user.id, e)
            }
        }
    }

}
