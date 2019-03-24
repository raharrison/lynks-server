package worker

import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class ReminderWorkerTest {

    private val reminderService = mockk<ReminderService>()
    private val notifyService = mockk<NotifyService>()
    private val entryService = mockk<EntryService>()
    private val context = TestCoroutineContext()

    @BeforeEach
    fun before() {
        every { reminderService.getAllReminders() } returns emptyList()
        every { entryService.get("e1") } returns null
        coEvery { notifyService.accept(any(), any()) } just Runs
        coEvery { notifyService.sendEmail(any(), any())} just Runs
        every { reminderService.isActive(any()) } returns true
    }

    @AfterEach
    fun after() {
        verify(exactly = 1) { reminderService.getAllReminders() }
    }

    @Test
    fun testSingleReminderInSameTimezone() = runBlocking(context) {
        val tz = ZoneId.systemDefault()
        val fire = Instant.now().plus(15, ChronoUnit.MINUTES).toEpochMilli()
        val fire2 = Instant.now().plus(45, ChronoUnit.MINUTES).toEpochMilli()
        val reminder = AdhocReminder("sc1", "e1", "message", fire, tz.id)
        val reminder2 = AdhocReminder("sc2", "e1", "message", fire2, tz.id)

        val worker = createWorker().worker()
        worker.send(ReminderWorkerRequest(reminder, CrudType.CREATE))
        worker.send(ReminderWorkerRequest(reminder2, CrudType.CREATE))
        worker.close()

        context.advanceTimeBy(14, TimeUnit.MINUTES)
        coVerify(exactly = 0) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 0) { notifyService.accept(any(), reminder2) }

        context.advanceTimeBy(1, TimeUnit.MINUTES)
        coVerify(exactly = 1) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 0) { notifyService.accept(any(), reminder2) }

        context.advanceTimeBy(30, TimeUnit.MINUTES)
        coVerify(exactly = 1) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 1) { notifyService.accept(any(), reminder2) }
    }

    @Test
    fun testSingleReminderDifferentTimezone() = runBlocking(context) {
        val tz = ZoneId.of("Asia/Singapore")
        val fire = Instant.now().plus(2, ChronoUnit.HOURS).toEpochMilli()
        val fire2 = Instant.now().plus(2, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES).toEpochMilli()
        val reminder = AdhocReminder("sc1", "e1", "message", fire, tz.id)
        val reminder2 = AdhocReminder("sc2", "e1", "message", fire2, tz.id)

        val worker = createWorker().worker()
        worker.send(ReminderWorkerRequest(reminder, CrudType.CREATE))
        worker.send(ReminderWorkerRequest(reminder2, CrudType.CREATE))
        worker.close()

        context.advanceTimeBy(118, TimeUnit.MINUTES)
        coVerify(exactly = 0) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 0) { notifyService.accept(any(), reminder2) }

        context.advanceTimeBy(2, TimeUnit.MINUTES)
        coVerify(exactly = 1) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 0) { notifyService.accept(any(), reminder2) }

        context.advanceTimeBy(30, TimeUnit.MINUTES)
        coVerify(exactly = 1) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 1) { notifyService.accept(any(), reminder2) }
    }

    @Test
    fun testRecurringReminderSameTimezone() = runBlocking(context) {
        val tz = ZoneId.systemDefault()
        val reminder = RecurringReminder("sc1", "e1", "message", "every 3 hours", tz.id)
        val reminder2 = RecurringReminder("sc1", "e1", "message", "every 30 minutes", tz.id)

        val worker = createWorker().worker()
        worker.send(ReminderWorkerRequest(reminder, CrudType.CREATE))
        worker.send(ReminderWorkerRequest(reminder2, CrudType.CREATE))

        context.advanceTimeBy(29, TimeUnit.MINUTES)
        coVerify(exactly = 0) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 0) { notifyService.accept(any(), reminder2) }

        context.advanceTimeBy(151, TimeUnit.MINUTES)
        coVerify(exactly = 1) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 6) { notifyService.accept(any(), reminder2) }

        context.advanceTimeBy(1, TimeUnit.HOURS)
        coVerify(exactly = 1) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 8) { notifyService.accept(any(), reminder2) }

        context.advanceTimeBy(2, TimeUnit.HOURS)
        coVerify(exactly = 2) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 12) { notifyService.accept(any(), reminder2) }

        context.advanceTimeBy(3, TimeUnit.HOURS)
        coVerify(exactly = 3) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 18) { notifyService.accept(any(), reminder2) }

        worker.close()
        Unit
    }

    @Test
    fun testRecurringReminderDifferentTimezone() = runBlocking(context) {
        val tz = ZoneId.of("Asia/Singapore")
        val reminder = RecurringReminder("sc1", "e1", "message", "every day 06:00", tz.id)

        val worker = createWorker().worker()
        worker.send(ReminderWorkerRequest(reminder, CrudType.CREATE))

        val day = LocalDateTime.now(tz).let {
            if(it.hour >= 6) it.plusDays(1)
            else it
        }
        val fireDate = ZonedDateTime.of(day.toLocalDate(), LocalTime.of(6, 0), tz)

        val until = ZonedDateTime.now().until(fireDate, ChronoUnit.MILLIS)

        // 100ms buffer
        context.advanceTimeBy((until / 2) + 100, TimeUnit.MILLISECONDS)
        coVerify(exactly = 0) { notifyService.accept(any(), reminder) }

        context.advanceTimeBy((until / 2) + 100, TimeUnit.MILLISECONDS)
        coVerify(exactly = 1) { notifyService.accept(any(), reminder) }

        worker.close()
        Unit
    }

    @Test
    fun testReminderNotExecutedIfDeleted() = runBlocking(context) {
        val fire = Instant.now().plus(15, ChronoUnit.MINUTES).toEpochMilli()
        val reminder = AdhocReminder("sc1", "e1", "message", fire, ZoneId.systemDefault().id)

        every { reminderService.isActive(reminder.reminderId) } returns false
        val worker = createWorker().worker()
        worker.send(ReminderWorkerRequest(reminder, CrudType.CREATE))
        worker.close()

        context.advanceTimeBy(16, TimeUnit.MINUTES)
        coVerify(exactly = 0) { notifyService.accept(any(), reminder) }
        verify(exactly = 1) { reminderService.isActive(reminder.reminderId) }
    }

    @Test
    fun testRecurringNotDeletedIfDeleted() = runBlocking(context) {
        val reminder = RecurringReminder("sc1", "e1", "message", "every 3 hours", ZoneId.systemDefault().id)

        every { reminderService.isActive(reminder.reminderId) } returns false
        val worker = createWorker().worker()
        worker.send(ReminderWorkerRequest(reminder, CrudType.CREATE))

        context.advanceTimeBy(185, TimeUnit.MINUTES)
        coVerify(exactly = 0) { notifyService.accept(any(), reminder) }
        verify(exactly = 1) { reminderService.isActive(reminder.reminderId) }

        worker.close()
        Unit
    }

    @Test
    fun testInitFromStart() = runBlocking(context) {
        val tz = ZoneId.systemDefault()
        val fire = Instant.now().plus(15, ChronoUnit.MINUTES).toEpochMilli()
        val reminder = AdhocReminder("sc1", "e1", "message", fire, tz.id)
        val recurring = RecurringReminder("sc1", "e1", "message", "every 3 hours", tz.id)

        every { reminderService.getAllReminders() } returns listOf(reminder, recurring)

        val worker = createWorker().worker()

        context.advanceTimeBy(185, TimeUnit.MINUTES)
        coVerify(exactly = 1) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 1) { notifyService.accept(any(), recurring) }
        verify(exactly = 2) { reminderService.isActive(reminder.reminderId) }

        worker.close()
        Unit
    }

    @Test
    fun testUpdateReminder() = runBlocking(context) {
        val tz = ZoneId.systemDefault()
        val fire = Instant.now().plus(2, ChronoUnit.HOURS).toEpochMilli()
        val reminder = AdhocReminder("sc1", "e1", "message", fire, tz.id)

        val worker = createWorker().worker()
        worker.send(ReminderWorkerRequest(reminder, CrudType.CREATE))

        val updatedReminder = reminder.copy(interval = reminder.interval + 1800000) // + 30 mins

        worker.send(ReminderWorkerRequest(updatedReminder, CrudType.UPDATE))

        worker.close()

        context.advanceTimeBy(125, TimeUnit.MINUTES)
        coVerify(exactly = 0) { notifyService.accept(any(), reminder) }

        context.advanceTimeBy(30, TimeUnit.MINUTES)
        coVerify(exactly = 1) { notifyService.accept(any(), updatedReminder) }
    }

    @Test
    fun testDeleteReminder() = runBlocking(context) {
        val tz = ZoneId.systemDefault()
        val fire = Instant.now().plus(2, ChronoUnit.HOURS).toEpochMilli()
        val reminder = AdhocReminder("sc1", "e1", "message", fire, tz.id)

        val worker = createWorker().worker()
        worker.send(ReminderWorkerRequest(reminder, CrudType.CREATE))

        worker.send(ReminderWorkerRequest(reminder, CrudType.DELETE))

        worker.close()

        context.advanceTimeBy(125, TimeUnit.MINUTES)
        coVerify(exactly = 0) { notifyService.accept(any(), reminder) }
    }

    private fun createWorker() = ReminderWorker(reminderService, entryService, notifyService).apply { runner = context }
}