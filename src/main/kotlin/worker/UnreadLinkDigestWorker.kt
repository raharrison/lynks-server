package worker

import entry.LinkService
import kotlinx.coroutines.time.delay
import notify.NotifyService
import user.Preferences
import user.UserService
import util.ResourceTemplater
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.*
import kotlin.math.min

class UnreadLinkDigestWorkerRequest(val preferences: Preferences, crudType: CrudType = CrudType.UPDATE) : VariableWorkerRequest(crudType) {
    override fun hashCode(): Int = 1
    override fun equals(other: Any?): Boolean = other is UnreadLinkDigestWorkerRequest
}

class UnreadLinkDigestWorker(private val linkService: LinkService, private val userService: UserService,
                             notifyService: NotifyService) : VariableChannelBasedWorker<UnreadLinkDigestWorkerRequest>(notifyService) {

    private val random = Random()

    override suspend fun beforeWork() {
        val preferences = userService.currentUserPreferences
        this.onChannelReceive(UnreadLinkDigestWorkerRequest(preferences, CrudType.CREATE))
    }

    override suspend fun doWork(input: UnreadLinkDigestWorkerRequest) {
        if (!input.preferences.digest) {
            log.debug("User does not have link digests enabled, exiting worker")
            return
        }

        // time till next fire
        val today = LocalDateTime.now()
        val fire = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .withHour(9)
                .withMinute(0)
        val initialDelay = today.until(fire, ChronoUnit.SECONDS)

        while (true) {
            log.info("Link digest worker sleeping for {} seconds until initial fire", initialDelay)
            delay(Duration.ofSeconds(initialDelay))
            sendDigest()
            log.info("Link digest worker sleeping for 7 days")
            delay(Duration.ofDays(7))
        }
    }

    private fun sendDigest() {
        val links = linkService.getUnread()
        if (links.isEmpty()) {
            log.info("Link digest worker found no unread links")
            return
        }

        val indexes = mutableSetOf<Int>()
        val limit = min(5, links.size)
        while (indexes.size < limit) {
            indexes += random.nextInt(links.size)
        }

        val random = indexes.map { links[it] }

        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault())
                .withZone(ZoneId.systemDefault())

        val content = random.map {
            mapOf("title" to it.title,
                    "url" to it.url,
                    "source" to it.source,
                    "date" to formatter.format(Instant.ofEpochMilli(it.dateUpdated)))
        }

        val template = ResourceTemplater("unread-link-digest.html")
        val email = template.apply(mapOf("entries" to content))

        log.info("Link digest worker sending unread link digest email")
        notifyService.sendEmail("Lynks - Weekly Digest", email)
    }

}