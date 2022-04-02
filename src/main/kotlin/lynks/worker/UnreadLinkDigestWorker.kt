package lynks.worker

import kotlinx.coroutines.time.delay
import lynks.entry.LinkService
import lynks.notify.NotifyService
import lynks.user.UserService
import lynks.util.ResourceTemplater
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.*
import kotlin.math.min

class UnreadLinkDigestWorker(
    private val notifyService: NotifyService,
    private val linkService: LinkService,
    private val userService: UserService
) : ChannelBasedWorker<String>() {

    private val random = Random()

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
            val emails = userService.getDigestEnabledEmails()
            if (emails.isNotEmpty()) {
                log.info("{} emails found for link digest", emails.size)
                sendDigests(emails)
            } else {
                log.info("No user emails found with digest enabled")
            }
            log.info("Link digest worker run completed, sleeping for 7 days")
            delay(Duration.ofDays(7))
        }
    }

    private fun sendDigests(emails: Set<String>) {
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
            mapOf(
                "title" to it.title,
                "url" to it.url,
                "source" to it.source,
                "date" to formatter.format(Instant.ofEpochMilli(it.dateUpdated))
            )
        }

        val template = ResourceTemplater("unread-link-digest.html")
        val emailContent = template.apply(mapOf("entries" to content))

        for (email in emails) {
            log.debug("Link digest worker sending email to {}", email)
            notifyService.sendEmail(email, "Lynks - Weekly Digest", emailContent)
        }
    }

}
