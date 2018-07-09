package worker

import io.mockk.*
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.test.TestCoroutineContext
import notify.NotifyService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import schedule.RecurringReminder
import schedule.Reminder
import schedule.ScheduleService
import schedule.ScheduleType
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class ReminderWorkerTest {

    private val scheduleService = mockk<ScheduleService>()
    private val notifyService = mockk<NotifyService>()
    private val context = TestCoroutineContext()

    @BeforeEach
    fun before() {
        every { scheduleService.getAllReminders() } returns emptyList()
        coEvery { notifyService.accept(any()) } just Runs
    }

    @AfterEach
    fun after() {
        verify(exactly = 1) { scheduleService.getAllReminders() }
    }

    @Test
    fun testSingleReminderInSameTimezone() = runBlocking(context) {
        val tz = ZoneId.systemDefault()
        val fire = Instant.now().plus(15, ChronoUnit.MINUTES).toEpochMilli()
        val fire2 = Instant.now().plus(45, ChronoUnit.MINUTES).toEpochMilli()
        val reminder = Reminder("sc1", "e1", ScheduleType.REMINDER, fire, tz.id)
        val reminder2 = Reminder("sc2", "e1", ScheduleType.REMINDER, fire2, tz.id)

        val worker = ReminderWorker(scheduleService, notifyService).apply { runner = context }.worker()
        worker.send(reminder)
        worker.send(reminder2)
        worker.close()

        context.advanceTimeBy(14, TimeUnit.MINUTES)
        coVerify(exactly = 0) { notifyService.accept(reminder) }
        coVerify(exactly = 0) { notifyService.accept(reminder2) }

        context.advanceTimeBy(1, TimeUnit.MINUTES)
        coVerify(exactly = 1) { notifyService.accept(reminder) }
        coVerify(exactly = 0) { notifyService.accept(reminder2) }

        context.advanceTimeBy(30, TimeUnit.MINUTES)
        coVerify(exactly = 1) { notifyService.accept(reminder) }
        coVerify(exactly = 1) { notifyService.accept(reminder2) }
    }

    @Test
    fun testSingleReminderDifferentTimezone() = runBlocking(context) {
        val tz = ZoneId.of("Asia/Singapore")
        val fire = Instant.now().plus(2, ChronoUnit.HOURS).toEpochMilli()
        val fire2 = Instant.now().plus(2, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES).toEpochMilli()
        val reminder = Reminder("sc1", "e1", ScheduleType.REMINDER, fire, tz.id)
        val reminder2 = Reminder("sc2", "e1", ScheduleType.REMINDER, fire2, tz.id)

        val worker = ReminderWorker(scheduleService, notifyService).apply { runner = context }.worker()
        worker.send(reminder)
        worker.send(reminder2)
        worker.close()

        context.advanceTimeBy(118, TimeUnit.MINUTES)
        coVerify(exactly = 0) { notifyService.accept(reminder) }
        coVerify(exactly = 0) { notifyService.accept(reminder2) }

        context.advanceTimeBy(2, TimeUnit.MINUTES)
        coVerify(exactly = 1) { notifyService.accept(reminder) }
        coVerify(exactly = 0) { notifyService.accept(reminder2) }

        context.advanceTimeBy(30, TimeUnit.MINUTES)
        coVerify(exactly = 1) { notifyService.accept(reminder) }
        coVerify(exactly = 1) { notifyService.accept(reminder2) }
    }

    @Test
    fun testRecurringReminderSameTimezone() = runBlocking(context) {
        val tz = ZoneId.systemDefault()
        val reminder = RecurringReminder("sc1", "e1", ScheduleType.RECURRING, "every 3 hours", tz.id)
        val reminder2 = RecurringReminder("sc1", "e1", ScheduleType.RECURRING, "every 30 minutes", tz.id)

        val worker = ReminderWorker(scheduleService, notifyService).apply { runner = context }.worker()
        worker.send(reminder)
        worker.send(reminder2)

        context.advanceTimeBy(29, TimeUnit.MINUTES)
        coVerify(exactly = 0) { notifyService.accept(reminder) }
        coVerify(exactly = 0) { notifyService.accept(reminder2) }

        context.advanceTimeBy(151, TimeUnit.MINUTES)
        coVerify(exactly = 1) { notifyService.accept(reminder) }
        coVerify(exactly = 6) { notifyService.accept(reminder2) }

        context.advanceTimeBy(1, TimeUnit.HOURS)
        coVerify(exactly = 1) { notifyService.accept(reminder) }
        coVerify(exactly = 8) { notifyService.accept(reminder2) }

        context.advanceTimeBy(2, TimeUnit.HOURS)
        coVerify(exactly = 2) { notifyService.accept(reminder) }
        coVerify(exactly = 12) { notifyService.accept(reminder2) }

        context.advanceTimeBy(3, TimeUnit.HOURS)
        coVerify(exactly = 3) { notifyService.accept(reminder) }
        coVerify(exactly = 18) { notifyService.accept(reminder2) }

        worker.close()
        Unit
    }

    @Test
    fun testRecurringReminderDifferentTimezone() = runBlocking(context) {
        val tz = ZoneId.of("Asia/Singapore")
        val reminder = RecurringReminder("sc1", "e1", ScheduleType.RECURRING, "every day 06:00", tz.id)

        val worker = ReminderWorker(scheduleService, notifyService).apply { runner = context }.worker()
        worker.send(reminder)

        val fireDate = ZonedDateTime.of(LocalDate.now(tz), LocalTime.of(6, 0), tz)
        val until = ZonedDateTime.now().until(fireDate, ChronoUnit.MILLIS)

        context.advanceTimeBy(until / 2, TimeUnit.MILLISECONDS)
        coVerify(exactly = 0) { notifyService.accept(reminder) }

        context.advanceTimeBy(until / 2, TimeUnit.MILLISECONDS)
        coVerify(exactly = 1) { notifyService.accept(reminder) }

        worker.close()
        Unit
    }
}