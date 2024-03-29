package lynks.worker

import kotlinx.coroutines.delay
import lynks.common.Link
import lynks.common.Note
import lynks.entry.EntryService
import lynks.notify.NewNotification
import lynks.notify.Notification
import lynks.notify.NotificationMethod
import lynks.notify.NotifyService
import lynks.reminder.*
import lynks.util.ResourceTemplater
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.max
import com.github.shyiko.skedule.Schedule as Skedule

class ReminderWorkerRequest(val reminder: Reminder, crudType: CrudType) : VariableWorkerRequest(crudType) {
    override fun hashCode(): Int = reminder.reminderId.hashCode()
    override fun equals(other: Any?): Boolean =
        other is ReminderWorkerRequest && this.reminder.reminderId == other.reminder.reminderId
}

class ReminderWorker(
    private val reminderService: ReminderService, private val entryService: EntryService,
    private val notifyService: NotifyService
) : VariableChannelBasedWorker<ReminderWorkerRequest>() {

    override suspend fun beforeWork() {
        super.beforeWork()
        reminderService.getAllActiveReminders().forEach {
            when (it) {
                is AdhocReminder -> launchJob { launchAdhocReminder(it) }
                is RecurringReminder -> launchJob { launchRecurringReminder(it) }
            }
        }
    }

    override suspend fun doWork(input: ReminderWorkerRequest) {
        // only launch jobs when the reminder is enabled
        if (input.reminder.status != ReminderStatus.ACTIVE) {
            return
        }
        when (input.reminder) {
            is AdhocReminder -> launchAdhocReminder(input.reminder)
            is RecurringReminder -> launchRecurringReminder(input.reminder)
        }
    }

    private suspend fun launchAdhocReminder(reminder: AdhocReminder) {
        val fireDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(reminder.interval), ZoneId.of(reminder.tz))
        log.info(
            "Launching single reminder entry={} id={} nextFire={}",
            reminder.entryId,
            reminder.reminderId,
            fireDate
        )
        val sleep = calcDelay(fireDate)
        log.info("Reminder worker sleeping for {}mins entry={} reminder={}",
            sleep / 1000 / 60, reminder.entryId, reminder.reminderId)
        delay(sleep)
        if (reminderService.isActive(reminder.reminderId)){
            reminderElapsed(reminder)
            log.info("Marking adhoc reminder as completed reminder={}", reminder.reminderId)
            reminderService.updateReminderStatus(reminder.reminderId, ReminderStatus.COMPLETED)
        }
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
                "Reminder worker sleeping for {}mins entry={} reminder={}",
                sleep / 1000 / 60,
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

        val message = if(reminder.message == null) "Reminder Elapsed" else reminder.message!!
        val notification = notifyService.create(NewNotification.reminder(message, reminder.entryId), false)

        for (notifyMethod in EnumSet.copyOf(reminder.notifyMethods)) {
            if (notifyMethod == NotificationMethod.WEB) {
                try {
                    notifyService.sendWebNotification(notification)
                } catch (e: Exception) {
                    log.error("Reminder web notification failed", e)
                }
            }

            if (notifyMethod == NotificationMethod.EMAIL) {
                try {
                    sendEmailNotification(reminder)
                } catch (e: Exception) {
                    log.error("Reminder email notification failed", e)
                }
            }

            if (notifyMethod == NotificationMethod.PUSHOVER) {
                try {
                    sendPushoverNotification(reminder, notification)
                } catch (e: Exception) {
                    log.error("Reminder pushover notification failed", e)
                }
            }

        }
    }

    private fun sendEmailNotification(reminder: Reminder) {
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

        notifyService.sendEmail("default", "Lynks - Reminder Elapsed", email)
    }

    private suspend fun sendPushoverNotification(reminder: Reminder, notification: Notification) {
        if (reminder.message == null) {
            notifyService.sendPushoverNotification(notification, null)
        } else {
            notifyService.sendPushoverNotification(notification, "Reminder Elapsed")
        }
    }

    private fun calcDelay(date: ZonedDateTime): Long {
        return max(0, ZonedDateTime.now().until(date, ChronoUnit.MILLIS))
    }

}
