package worker

import entry.LinkService
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.delay
import notify.NotifyService
import user.Preferences
import util.ResourceTemplater
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.*
import java.util.concurrent.TimeUnit

class UnreadLinkDigestWorker(private val linkService: LinkService, private val notifyService: NotifyService): Worker<Preferences>(notifyService) {

    private val random = Random()

    private var workerJob: Job? = null

    // TODO: beforeWork to start worker with initial preferences

    override suspend fun doWork(input: Preferences) {
        if(!input.digest) {
            workerJob?.cancel()
            return
        }

        // time till next fire
        val today = LocalDateTime.now()
        val fire = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .withHour(9)
                .withMinute(0)
        val initialDelay = today.until(fire, ChronoUnit.SECONDS)

        workerJob = launchJob {
            while (true) {
                delay(initialDelay, TimeUnit.SECONDS)
                sendDigest()
                delay(7, TimeUnit.DAYS)
            }
        }
    }

    private fun sendDigest() {
        val links = linkService.getUnread()
        if(links.isEmpty()) return

        val indexes = mutableSetOf<Int>()
        val limit = Math.min(5, links.size)
        while(indexes.size <= limit) {
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

        notifyService.sendEmail("Lynks - Weekly Digest", email)
    }

}