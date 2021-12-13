package worker

import common.Link
import common.Note
import entry.EntryAuditService
import entry.EntryService
import kotlinx.coroutines.delay
import notify.Notification
import notify.NotificationMethod
import notify.NotifyService
import reminder.AdhocReminder
import reminder.RecurringReminder
import reminder.Reminder
import reminder.ReminderService
import util.ResourceTemplater
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.max
import com.github.shyiko.skedule.Schedule as Skedule

class ReminderWorkerRequest(val reminder: Reminder, crudType: CrudType) : VariableWorkerRequest(crudType) {
    override fun hashCode(): Int = reminder.reminderId.hashCode()
    override fun equals(other: Any?): Boolean =
        other is ReminderWorkerRequest && this.reminder.reminderId == other.reminder.reminderId
}

class ReminderWorker(
    private val reminderService: ReminderService, private val entryService: EntryService,
    notifyService: NotifyService, entryAuditService: EntryAuditService
) : VariableChannelBasedWorker<ReminderWorkerRequest>(notifyService, entryAuditService) {

    override suspend fun beforeWork() {
        super.beforeWork()
        reminderService.getAllReminders().forEach {
            when (it) {
                is AdhocReminder -> launchJob { launchReminder(it) }
                is RecurringReminder -> launchJob { launchRecurringReminder(it) }
            }
        }
    }

    override suspend fun doWork(input: ReminderWorkerRequest) {
        when (input.reminder) {
            is AdhocReminder -> launchReminder(input.reminder)
            is RecurringReminder -> launchRecurringReminder(input.reminder)
        }
    }

    private suspend fun launchReminder(reminder: AdhocReminder) {
        val fireDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(reminder.interval), ZoneId.of(reminder.tz))
        log.info(
            "Launching single reminder entry={} id={} nextFire={}",
            reminder.entryId,
            reminder.reminderId,
            fireDate
        )
        val sleep = calcDelay(fireDate)
        log.info("Reminder worker sleeping for {}ms entry={} reminder={}", sleep, reminder.entryId, reminder.reminderId)
        delay(sleep)
        if (reminderService.isActive(reminder.reminderId))
            reminderElapsed(reminder)
    }

    private suspend fun launchRecurringReminder(reminder: RecurringReminder) {
        val fire = reminder.fire
        val tz = ZoneId.of(reminder.tz)
        val schedule = Skedule.parse(fire)
        while (true) {
            val next = schedule.next(ZonedDateTime.now(tz))
            log.info(
                "Launching recurring reminder entry={} id={} nextFire={}",
                reminder.entryId,
                reminder.reminderId,
                next
            )
            val sleep = calcDelay(next)
            log.info(
                "Reminder worker sleeping for {}ms entry={} reminder={}",
                sleep,
                reminder.entryId,
                reminder.reminderId
            )
            delay(sleep)
            if (reminderService.isActive(reminder.reminderId)) reminderElapsed(reminder)
            else break
        }
    }

    private suspend fun reminderElapsed(reminder: Reminder) {
        log.info("Reminder elapsed entry={} reminder={}", reminder.entryId, reminder.reminderId)

        if (reminder.notifyMethod == NotificationMethod.PUSH || reminder.notifyMethod == NotificationMethod.BOTH) {
            // send notification
            if (reminder.message == null) {
                sendNotification(Notification.reminder(), reminder)
            } else {
                sendNotification(Notification.reminder(reminder.message!!), reminder)
            }
        }

        if (reminder.notifyMethod == NotificationMethod.EMAIL || reminder.notifyMethod == NotificationMethod.BOTH) {
            // send email
            val title = when (val entry = entryService.get(reminder.entryId)) {
                is Link -> entry.title
                is Note -> entry.title
                else -> entry?.javaClass?.simpleName
            }
            val content = mapOf(
                "title" to title,
                "spec" to reminder.spec,
                "message" to reminder.message
            )

            val template = ResourceTemplater("reminder.html")
            val email = template.apply(content)

            notifyService.sendEmail("Lynks - Reminder Elapsed", email)
        }
    }

    private fun calcDelay(date: ZonedDateTime): Long {
        return max(0, ZonedDateTime.now().until(date, ChronoUnit.MILLIS))
    }

}
