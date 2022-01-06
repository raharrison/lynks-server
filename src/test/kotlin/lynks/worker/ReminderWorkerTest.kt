package lynks.worker

import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import lynks.entry.EntryAuditService
import lynks.entry.EntryService
import lynks.notify.NotificationMethod
import lynks.notify.NotifyService
import lynks.reminder.AdhocReminder
import lynks.reminder.RecurringReminder
import lynks.reminder.ReminderService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

@ExperimentalCoroutinesApi
class ReminderWorkerTest {

    private val reminderService = mockk<ReminderService>()
    private val notifyService = mockk<NotifyService>()
    private val entryService = mockk<EntryService>()
    private val entryAuditService = mockk<EntryAuditService>(relaxUnitFun = true)

    @BeforeEach
    fun before() {
        every { reminderService.getAllReminders() } returns emptyList()
        every { entryService.get("e1") } returns null
        coEvery { notifyService.accept(any(), any()) } just Runs
        coEvery { notifyService.sendEmail(any(), any()) } just Runs
        every { reminderService.isActive(any()) } returns true
    }

    @AfterEach
    fun after() {
        verify(exactly = 1) { reminderService.getAllReminders() }
    }

    @Test
    fun testSingleReminderInSameTimezone() = runTest {
        val tz = ZoneId.systemDefault()
        val fire = Instant.now().plus(15, ChronoUnit.MINUTES).toEpochMilli()
        val fire2 = Instant.now().plus(45, ChronoUnit.MINUTES).toEpochMilli()
        val reminder = AdhocReminder("sc1", "e1", NotificationMethod.PUSH, "message", fire, tz.id, 1234, 1234)
        val reminder2 = AdhocReminder("sc2", "e1", NotificationMethod.BOTH, "message", fire2, tz.id, 1234, 1234)

        val worker = createWorker(coroutineContext)
        val send = worker.worker()
        send.send(ReminderWorkerRequest(reminder, CrudType.CREATE))
        send.send(ReminderWorkerRequest(reminder2, CrudType.CREATE))

        advanceTimeBy(TimeUnit.MINUTES.toMillis(14))
        coVerify(exactly = 0) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 0) { notifyService.accept(any(), reminder2) }

        advanceTimeBy(TimeUnit.MINUTES.toMillis(1))
        coVerify(exactly = 1) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 0) { notifyService.accept(any(), reminder2) }

        advanceTimeBy(TimeUnit.MINUTES.toMillis(30))
        coVerify(exactly = 1) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 1) { notifyService.accept(any(), reminder2) }

        send.close()
        worker.cancelAll()
    }

    @Test
    fun testSingleReminderDifferentTimezone() = runTest {
        val tz = ZoneId.of("Asia/Singapore")
        val fire = Instant.now().plus(2, ChronoUnit.HOURS).toEpochMilli()
        val fire2 = Instant.now().plus(2, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES).toEpochMilli()
        val reminder = AdhocReminder("sc1", "e1", NotificationMethod.PUSH, "message", fire, tz.id, 1234, 1234)
        val reminder2 = AdhocReminder("sc2", "e1", NotificationMethod.BOTH, "message", fire2, tz.id, 1234, 1234)

        val worker = createWorker(coroutineContext)
        val send = worker.worker()
        send.send(ReminderWorkerRequest(reminder, CrudType.CREATE))
        send.send(ReminderWorkerRequest(reminder2, CrudType.CREATE))

        advanceTimeBy(TimeUnit.MINUTES.toMillis(118))
        coVerify(exactly = 0) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 0) { notifyService.accept(any(), reminder2) }

        advanceTimeBy(TimeUnit.MINUTES.toMillis(2))
        coVerify(exactly = 1) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 0) { notifyService.accept(any(), reminder2) }

        advanceTimeBy(TimeUnit.MINUTES.toMillis(30))
        coVerify(exactly = 1) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 1) { notifyService.accept(any(), reminder2) }

        send.close()
        worker.cancelAll()
    }

    @Test
    fun testRecurringReminderSameTimezone() = runTest {
        val tz = ZoneId.systemDefault()
        val reminder = RecurringReminder("sc2", "e1", NotificationMethod.BOTH, "message", "every 30 minutes", tz.id, 1234, 1234)

        val worker = createWorker(coroutineContext)
        val send = worker.worker()
        send.send(ReminderWorkerRequest(reminder, CrudType.CREATE))

        advanceTimeBy(TimeUnit.MINUTES.toMillis(25))
        coVerify(exactly = 0) { notifyService.accept(any(), reminder) }

        advanceTimeBy(TimeUnit.MINUTES.toMillis(155))
        coVerify(exactly = 6) { notifyService.accept(any(), reminder) }

        advanceTimeBy(TimeUnit.HOURS.toMillis(1))
        coVerify(exactly = 8) { notifyService.accept(any(), reminder) }

        advanceTimeBy(TimeUnit.HOURS.toMillis(2))
        coVerify(exactly = 12) { notifyService.accept(any(), reminder) }

        advanceTimeBy(TimeUnit.HOURS.toMillis(3))
        coVerify(exactly = 18) { notifyService.accept(any(), reminder) }

        send.close()
        worker.cancelAll()
    }

    @Test
    fun testRecurringReminderDifferentTimezone() = runTest {
        val tz = ZoneId.of("Asia/Singapore")
        val reminder = RecurringReminder("sc1", "e1", NotificationMethod.PUSH, "message", "every day 06:00", tz.id, 1234, 1234)

        val worker = createWorker(coroutineContext)
        val send = worker.worker()
        send.send(ReminderWorkerRequest(reminder, CrudType.CREATE))

        val day = LocalDateTime.now(tz).let {
            if (it.hour >= 6) it.plusDays(1)
            else it
        }
        val fireDate = ZonedDateTime.of(day.toLocalDate(), LocalTime.of(6, 0), tz)

        val until = ZonedDateTime.now().until(fireDate, ChronoUnit.MILLIS)

        // 100ms buffer
        advanceTimeBy(TimeUnit.MILLISECONDS.toMillis((until / 2) + 100))
        coVerify(exactly = 0) { notifyService.accept(any(), reminder) }

        advanceTimeBy(TimeUnit.MILLISECONDS.toMillis((until / 2) + 100))
        coVerify(exactly = 1) { notifyService.accept(any(), reminder) }

        send.close()
        worker.cancelAll()
    }

    @Test
    fun testReminderNotExecutedIfDeleted() = runTest {
        val fire = Instant.now().plus(15, ChronoUnit.MINUTES).toEpochMilli()
        val reminder = AdhocReminder("sc1", "e1", NotificationMethod.BOTH, "message", fire, ZoneId.systemDefault().id, 1234, 1234)

        every { reminderService.isActive(reminder.reminderId) } returns false
        val worker = createWorker(coroutineContext)
        val send = worker.worker()
        send.send(ReminderWorkerRequest(reminder, CrudType.CREATE))

        advanceTimeBy(TimeUnit.MINUTES.toMillis(16))
        coVerify(exactly = 0) { notifyService.accept(any(), reminder) }
        verify(exactly = 1) { reminderService.isActive(reminder.reminderId) }

        send.close()
        worker.cancelAll()
    }

    @Test
    fun testRecurringNotDeletedIfDeleted() = runTest {
        val reminder = RecurringReminder("sc1", "e1", NotificationMethod.BOTH, "message", "every 3 hours", ZoneId.systemDefault().id, 1234, 1234)

        every { reminderService.isActive(reminder.reminderId) } returns false
        val worker = createWorker(coroutineContext)
        val send = worker.worker()
        send.send(ReminderWorkerRequest(reminder, CrudType.CREATE))

        advanceTimeBy(TimeUnit.MINUTES.toMillis(185))
        coVerify(exactly = 0) { notifyService.accept(any(), reminder) }
        verify(exactly = 1) { reminderService.isActive(reminder.reminderId) }

        send.close()
        worker.cancelAll()
    }

    @Test
    fun testInitFromStart() = runTest {
        val tz = ZoneId.systemDefault()
        val fire = Instant.now().plus(15, ChronoUnit.MINUTES).toEpochMilli()
        val reminder = AdhocReminder("sc1", "e1", NotificationMethod.PUSH, "message", fire, tz.id, 1234, 1234)
        val recurring = RecurringReminder("sc1", "e1", NotificationMethod.BOTH, "message", "every 3 hours", tz.id, 1234, 1234)

        every { reminderService.getAllReminders() } returns listOf(reminder, recurring)

        val worker = createWorker(coroutineContext)
        val send = worker.worker()

        advanceTimeBy(TimeUnit.MINUTES.toMillis(185))
        coVerify(exactly = 1) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 1) { notifyService.accept(any(), recurring) }
        verify(exactly = 2) { reminderService.isActive(reminder.reminderId) }

        send.close()
        worker.cancelAll()
    }

    @Test
    fun testUpdateReminder() = runTest {
        val tz = ZoneId.systemDefault()
        val fire = Instant.now().plus(2, ChronoUnit.HOURS).toEpochMilli()
        val reminder = AdhocReminder("sc1", "e1", NotificationMethod.PUSH, "message", fire, tz.id, 1234, 1234)

        val worker = createWorker(coroutineContext)
        val send = worker.worker()
        send.send(ReminderWorkerRequest(reminder, CrudType.CREATE))

        val updatedReminder = reminder.copy(interval = reminder.interval + 1800000) // + 30 mins

        send.send(ReminderWorkerRequest(updatedReminder, CrudType.UPDATE))

        advanceTimeBy(TimeUnit.MINUTES.toMillis(125))
        coVerify(exactly = 0) { notifyService.accept(any(), reminder) }

        advanceTimeBy(TimeUnit.MINUTES.toMillis(30))
        coVerify(exactly = 1) { notifyService.accept(any(), updatedReminder) }

        send.close()
        worker.cancelAll()
    }

    @Test
    fun testDeleteReminder() = runTest {
        val tz = ZoneId.systemDefault()
        val fire = Instant.now().plus(2, ChronoUnit.HOURS).toEpochMilli()
        val reminder = AdhocReminder("sc1", "e1", NotificationMethod.BOTH, "message", fire, tz.id, 1234, 1234)

        val worker = createWorker(coroutineContext)
        val send = worker.worker()

        send.send(ReminderWorkerRequest(reminder, CrudType.CREATE))
        send.send(ReminderWorkerRequest(reminder, CrudType.DELETE))

        advanceTimeBy(TimeUnit.MINUTES.toMillis(125))
        coVerify(exactly = 0) { notifyService.accept(any(), reminder) }

        send.close()
        worker.cancelAll()
    }

    private fun createWorker(context: CoroutineContext) = ReminderWorker(reminderService, entryService, notifyService, entryAuditService)
        .apply { runner = context }
}
