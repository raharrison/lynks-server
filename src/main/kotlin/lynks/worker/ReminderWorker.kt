package lynks.worker

import kotlinx.coroutines.delay
import lynks.common.UserId
import lynks.notify.NewNotification
import lynks.notify.Notification
import lynks.notify.NotificationMethod
import lynks.notify.NotifyService
import lynks.reminder.*
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.max
import com.github.shyiko.skedule.Schedule as Skedule

class ReminderWorkerRequest(val userId: UserId, val reminder: Reminder, crudType: CrudType) : VariableWorkerRequest(crudType) {
    override fun hashCode(): Int = reminder.reminderId.hashCode()
    override fun equals(other: Any?): Boolean =
        other is ReminderWorkerRequest && this.reminder.reminderId == other.reminder.reminderId
}

class ReminderWorker(
    private val reminderService: ReminderService,
    private val notifyService: NotifyService
) : VariableChannelBasedWorker<ReminderWorkerRequest>() {

    override suspend fun beforeWork() {
        super.beforeWork()
        // Tracked like any other request, so a later update or delete can cancel it
        reminderService.getAllActiveReminders().forEach { (userId, reminder) ->
            onChannelReceive(ReminderWorkerRequest(userId, reminder, CrudType.CREATE))
        }
    }

    override suspend fun doWork(input: ReminderWorkerRequest) {
        // only launch jobs when the reminder is enabled
        if (input.reminder.status != ReminderStatus.ACTIVE) {
            return
        }
        when (input.reminder) {
            is AdhocReminder -> launchAdhocReminder(input.userId, input.reminder)
            is RecurringReminder -> launchRecurringReminder(input.userId, input.reminder)
        }
    }

    private suspend fun launchAdhocReminder(userId: UserId, reminder: AdhocReminder) {
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
            reminderElapsed(userId, reminder)
            log.info("Marking adhoc reminder as completed reminder={}", reminder.reminderId)
            reminderService.updateReminderStatus(reminder.reminderId, ReminderStatus.COMPLETED)
        }
    }

    private suspend fun launchRecurringReminder(userId: UserId, reminder: RecurringReminder) {
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
            if (reminderService.isActive(reminder.reminderId)) reminderElapsed(userId, reminder)
            else break
        }
    }

    private suspend fun reminderElapsed(userId: UserId, reminder: Reminder) {
        log.info("Reminder elapsed entry={} reminder={}", reminder.entryId, reminder.reminderId)

        val message = reminder.message ?: "Reminder Elapsed"
        // the in-app notification is what PUSH delivers, and the UI polls for it
        val notification = notifyService.create(userId, NewNotification.reminder(message, reminder.entryId))

        if (NotificationMethod.JOLT in reminder.notifyMethods) {
            try {
                sendJoltNotification(userId, reminder, notification)
            } catch (e: Exception) {
                log.error("Reminder jolt notification failed", e)
            }
        }
    }

    private suspend fun sendJoltNotification(userId: UserId, reminder: Reminder, notification: Notification) {
        val title = if (reminder.message == null) null else "Reminder Elapsed"
        notifyService.sendJoltNotification(userId, notification, title)
    }

    private fun calcDelay(date: ZonedDateTime): Long {
        return max(0, ZonedDateTime.now().until(date, ChronoUnit.MILLIS))
    }

}
