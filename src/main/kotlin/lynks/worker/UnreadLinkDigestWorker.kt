package lynks.worker

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
        // time till next fire on Monday mornings
        val today = LocalDateTime.now()
        val fire = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            .withHour(9)
            .withMinute(0)
        val initialDelay = today.until(fire, ChronoUnit.SECONDS)

        while (true) {
            log.debug("Link digest worker sleeping for {} hours until initial fire", initialDelay / 60 / 24)
            delay(Duration.ofSeconds(initialDelay))
            regenerate()
            log.info("Link digest worker run completed, sleeping for 7 days")
            delay(Duration.ofDays(7))
        }
    }

    private suspend fun regenerate() {
        val digest = digestService.generate()
        if (digest == null) {
            log.info("Link digest worker found no unread links, no digest generated")
            return
        }
        if (!userService.isDigestEnabled()) {
            log.info("Digest regenerated but no user has digest notifications enabled")
            return
        }
        notifyService.create(NewNotification.digest("Weekly digest ready with ${digest.links.size} unread links"))
    }

}
