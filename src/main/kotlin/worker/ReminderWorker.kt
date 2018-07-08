package worker

import kotlinx.coroutines.experimental.delay
import notify.NotifyService
import schedule.RecurringReminder
import schedule.Reminder
import schedule.Schedule
import schedule.ScheduleService
import util.loggerFor
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import com.github.shyiko.skedule.Schedule as Skedule

private val logger = loggerFor<ReminderWorker>()

class ReminderWorker(private val scheduleService: ScheduleService,
                     private val notifyService: NotifyService): Worker<Schedule>() {

    override suspend fun beforeWork() {
        super.beforeWork()
        scheduleService.getAllReminders().forEach {
            when(it) {
                is Reminder -> launchJob({launchReminder(it)})
                is RecurringReminder -> launchJob({launchRecurringReminder(it)})
            }
        }
    }

    override suspend fun doWork(input: Schedule) {
        when(input) {
            is Reminder -> launchReminder(input)
            is RecurringReminder -> launchRecurringReminder(input)
        }
    }

    private suspend fun launchReminder(reminder: Reminder) {
        logger.info("Reminder = $reminder")
        val fireDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(reminder.interval), ZoneId.of(reminder.tz))
        val sleep = calcDelay(fireDate)
        logger.info("Sleeping for ${sleep}ms")
        delay(sleep, TimeUnit.MILLISECONDS)
        reminderElapsed(reminder)
    }

    private suspend fun launchRecurringReminder(reminder: RecurringReminder) {
        val fire = reminder.fire
        val tz = ZoneId.of(reminder.tz)
        val schedule = Skedule.parse(fire)
        while(true) {
            val next = schedule.next(ZonedDateTime.now(tz))
            val sleep = calcDelay(next)
            delay(sleep, TimeUnit.MILLISECONDS)
            reminderElapsed(reminder)
        }
    }

    private fun reminderElapsed(reminder: Schedule) {
        notifyService.accept(reminder)
    }

    private fun calcDelay(date: ZonedDateTime): Long {
        return Math.max(0, ZonedDateTime.now().until(date, ChronoUnit.MILLIS))
    }

}