package worker

import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.io.ClassPathTemplateLoader
import entry.LinkService
import kotlinx.coroutines.experimental.delay
import notify.NotifyService
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

    override suspend fun doWork(input: Any?) {

        // time till next fire
        val today = LocalDateTime.now()
        val fire = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .withHour(9)
                .withMinute(0)
        val initialDelay = today.until(fire, ChronoUnit.SECONDS)

        while(true) {
            delay(initialDelay, TimeUnit.SECONDS)
            sendDigest()
            delay(7, TimeUnit.DAYS)
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

        val email = generateEmail(content)

        notifyService.sendEmail("Lynks - Weekly Digest", email)
    }

    private fun generateEmail(entries: List<Map<String, String>>): String {
        val loader = ClassPathTemplateLoader()
        loader.prefix = "/resources/templates"
        loader.suffix = ".html"
        val handlebars = Handlebars(loader)

        val template = handlebars.compile("unread-link-digest")

        return template.apply(mapOf("entries" to entries))
    }

}