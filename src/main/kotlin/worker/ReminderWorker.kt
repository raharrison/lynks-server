package worker

import entry.EntryService
import kotlinx.coroutines.experimental.delay
import notify.Notification
import notify.NotifyService
import reminder.AdhocReminder
import reminder.RecurringReminder
import reminder.Reminder
import reminder.ReminderService
import util.ResourceTemplater
import util.loggerFor
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import com.github.shyiko.skedule.Schedule as Skedule

private val logger = loggerFor<ReminderWorker>()

class ReminderWorkerRequest(val reminder: Reminder, crudType: CrudType): VariableWorkerRequest(crudType) {
    override fun hashCode(): Int = reminder.reminderId.hashCode()
    override fun equals(other: Any?): Boolean = other is ReminderWorkerRequest && this.reminder.reminderId == other.reminder.reminderId
}

class ReminderWorker(private val reminderService: ReminderService, private val entryService: EntryService,
                     notifyService: NotifyService) : VariableChannelBasedWorker<ReminderWorkerRequest>(notifyService) {

    override suspend fun beforeWork() {
        super.beforeWork()
        reminderService.getAllReminders().forEach {
            when (it) {
                is AdhocReminder -> launchJob({ launchReminder(it) })
                is RecurringReminder -> launchJob({ launchRecurringReminder(it) })
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
        logger.info("Reminder = $reminder")
        val fireDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(reminder.interval), ZoneId.of(reminder.tz))
        val sleep = calcDelay(fireDate)
        logger.info("Sleeping for ${sleep}ms")
        delay(sleep, TimeUnit.MILLISECONDS)
        if (reminderService.isActive(reminder.reminderId))
            reminderElapsed(reminder)
    }

    private suspend fun launchRecurringReminder(reminder: RecurringReminder) {
        val fire = reminder.fire
        val tz = ZoneId.of(reminder.tz)
        val schedule = Skedule.parse(fire)
        while (true) {
            val next = schedule.next(ZonedDateTime.now(tz))
            val sleep = calcDelay(next)
            logger.info("Sleeping for ${sleep}ms")
            delay(sleep, TimeUnit.MILLISECONDS)
            if (reminderService.isActive(reminder.reminderId)) reminderElapsed(reminder)
            else break
        }
    }

    private suspend fun reminderElapsed(reminder: Reminder) {
        // send notification
        if(reminder.message == null) {
            sendNotification(Notification.reminder(), reminder)
        }
        else {
            sendNotification(Notification.reminder(reminder.message!!), reminder)
        }

        // send email
        val entry = entryService.get(reminder.entryId)
        val content = mapOf("title" to entry?.title,
                    "spec" to reminder.spec,
                    "message" to reminder.message)

        val template = ResourceTemplater("reminder.html")
        val email = template.apply(content)

        notifyService.sendEmail("Lynks - Reminder Elapsed", email)
    }

    private fun calcDelay(date: ZonedDateTime): Long {
        return Math.max(0, ZonedDateTime.now().until(date, ChronoUnit.MILLIS))
    }

}