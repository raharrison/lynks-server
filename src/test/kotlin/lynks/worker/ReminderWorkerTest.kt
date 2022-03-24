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
import lynks.reminder.ReminderStatus
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
        every { reminderService.getAllActiveReminders() } returns emptyList()
        every { reminderService.updateReminderStatus(any(), any()) } returns 1
        every { entryService.get("e1") } returns null
        coEvery { notifyService.accept(any(), any()) } just Runs
        coEvery { notifyService.sendEmail(any(), any()) } just Runs
        coEvery { notifyService.sendPushoverNotification(any(), any()) } just Runs
        every { reminderService.isActive(any()) } returns true
    }

    @AfterEach
    fun after() {
        // always called before worker starts
        verify(exactly = 1) { reminderService.getAllActiveReminders() }
    }

    @Test
    fun testSingleReminderInSameTimezone() = runTest {
        val tz = ZoneId.systemDefault()
        val fire = Instant.now().plus(15, ChronoUnit.MINUTES).toEpochMilli()
        val fire2 = Instant.now().plus(45, ChronoUnit.MINUTES).toEpochMilli()
        val reminder = AdhocReminder("sc1", "e1",
            listOf(NotificationMethod.WEB), "message", fire, tz.id, ReminderStatus.ACTIVE,
            1234, 1234)
        val reminder2 = AdhocReminder("sc2", "e1",
            listOf(NotificationMethod.WEB, NotificationMethod.EMAIL), "message", fire2,
            tz.id, ReminderStatus.ACTIVE, 1234, 1234)

        val worker = createWorker(coroutineContext)
        val send = worker.worker()
        send.send(ReminderWorkerRequest(reminder, CrudType.CREATE))
        send.send(ReminderWorkerRequest(reminder2, CrudType.CREATE))

        advanceTimeBy(TimeUnit.MINUTES.toMillis(14))
        coVerify(exactly = 0) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 0) { notifyService.accept(any(), reminder2) }
        coVerify(exactly = 0) { notifyService.sendEmail(any(), any()) }
        coVerify(exactly = 0) { notifyService.sendPushoverNotification(any(), any()) }

        advanceTimeBy(TimeUnit.MINUTES.toMillis(1))
        coVerify(exactly = 1) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 0) { notifyService.accept(any(), reminder2) }
        coVerify(exactly = 0) { notifyService.sendEmail(any(), any()) }
        coVerify(exactly = 0) { notifyService.sendPushoverNotification(any(), any()) }

        advanceTimeBy(TimeUnit.MINUTES.toMillis(35))
        send.close()
        worker.cancelAll()

        coVerify(exactly = 1) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 1) { notifyService.accept(any(), reminder2) }
        coVerify(exactly = 1) { notifyService.sendEmail(any(), any()) }
        coVerify(exactly = 0) { notifyService.sendPushoverNotification(any(), any()) }
        coVerify(exactly = 1) { reminderService.updateReminderStatus(reminder.reminderId, ReminderStatus.COMPLETED) }
        coVerify(exactly = 1) { reminderService.updateReminderStatus(reminder2.reminderId, ReminderStatus.COMPLETED) }
    }

    @Test
    fun testSingleReminderDifferentTimezone() = runTest {
        val tz = ZoneId.of("Asia/Singapore")
        val fire = Instant.now().plus(2, ChronoUnit.HOURS).toEpochMilli()
        val fire2 = Instant.now().plus(2, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES).toEpochMilli()
        val reminder = AdhocReminder("sc1", "e1",
            listOf(NotificationMethod.WEB, NotificationMethod.PUSHOVER), "message", fire, tz.id,
            ReminderStatus.ACTIVE, 1234, 1234)
        val reminder2 = AdhocReminder("sc2", "e1",
            listOf(NotificationMethod.WEB), "message", fire2,
            tz.id, ReminderStatus.ACTIVE, 1234, 1234)

        val worker = createWorker(coroutineContext)
        val send = worker.worker()
        send.send(ReminderWorkerRequest(reminder, CrudType.CREATE))
        send.send(ReminderWorkerRequest(reminder2, CrudType.CREATE))

        advanceTimeBy(TimeUnit.MINUTES.toMillis(118))
        coVerify(exactly = 0) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 0) { notifyService.accept(any(), reminder2) }
        coVerify(exactly = 0) { notifyService.sendEmail(any(), any()) }
        coVerify(exactly = 0) { notifyService.sendPushoverNotification(any(), any()) }

        advanceTimeBy(TimeUnit.MINUTES.toMillis(2))
        coVerify(exactly = 1) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 1) { notifyService.sendPushoverNotification(any(), any()) }
        coVerify(exactly = 0) { notifyService.accept(any(), reminder2) }
        coVerify(exactly = 0) { notifyService.sendEmail(any(), any()) }

        advanceTimeBy(TimeUnit.MINUTES.toMillis(35))
        send.close()
        worker.cancelAll()

        coVerify(exactly = 1) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 1) { notifyService.sendPushoverNotification(any(), any()) }
        coVerify(exactly = 1) { reminderService.updateReminderStatus(reminder.reminderId, ReminderStatus.COMPLETED) }
        coVerify(exactly = 1) { notifyService.accept(any(), reminder2) }
        coVerify(exactly = 1) { reminderService.updateReminderStatus(reminder2.reminderId, ReminderStatus.COMPLETED) }
        coVerify(exactly = 0) { notifyService.sendEmail(any(), any()) }
    }

    @Test
    fun testRecurringReminderSameTimezone() = runTest {
        val tz = ZoneId.systemDefault()
        val reminder = RecurringReminder("sc2", "e1",
            listOf(NotificationMethod.WEB, NotificationMethod.PUSHOVER), "message", "every 30 minutes",
            tz.id, ReminderStatus.ACTIVE, 1234, 1234)

        val worker = createWorker(coroutineContext)
        val send = worker.worker()
        send.send(ReminderWorkerRequest(reminder, CrudType.CREATE))

        advanceTimeBy(TimeUnit.MINUTES.toMillis(25))
        coVerify(exactly = 0) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 0) { notifyService.sendPushoverNotification(any(), any()) }

        advanceTimeBy(TimeUnit.MINUTES.toMillis(160))
        coVerify(exactly = 6) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 6) { notifyService.sendPushoverNotification(any(), any()) }

        advanceTimeBy(TimeUnit.HOURS.toMillis(1))
        coVerify(exactly = 8) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 8) { notifyService.sendPushoverNotification(any(), any()) }

        advanceTimeBy(TimeUnit.HOURS.toMillis(2))
        coVerify(exactly = 12) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 12) { notifyService.sendPushoverNotification(any(), any()) }

        advanceTimeBy(TimeUnit.HOURS.toMillis(3))
        send.close()
        worker.cancelAll()

        coVerify(exactly = 18) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 18) { notifyService.sendPushoverNotification(any(), any()) }
    }

    @Test
    fun testRecurringReminderDifferentTimezone() = runTest {
        val tz = ZoneId.of("Asia/Singapore")
        val reminder = RecurringReminder("sc1", "e1",
            listOf(NotificationMethod.WEB), "message", "every day 06:00",
            tz.id, ReminderStatus.ACTIVE, 1234, 1234)

        val worker = createWorker(coroutineContext)
        val send = worker.worker()
        send.send(ReminderWorkerRequest(reminder, CrudType.CREATE))

        val day = LocalDateTime.now(tz).let {
            if (it.hour >= 6) it.plusDays(1)
            else it
        }
        val fireDate = ZonedDateTime.of(day.toLocalDate(), LocalTime.of(6, 0), tz)

        val until = ZonedDateTime.now().until(fireDate, ChronoUnit.MILLIS)

        // 200ms buffer
        advanceTimeBy(TimeUnit.MILLISECONDS.toMillis((until / 2) + 200))
        coVerify(exactly = 0) { notifyService.accept(any(), reminder) }

        advanceTimeBy(TimeUnit.MILLISECONDS.toMillis((until / 2) + 200))
        send.close()
        worker.cancelAll()

        coVerify(exactly = 1) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 0) { notifyService.sendEmail(any(), any()) }
        coVerify(exactly = 0) { notifyService.sendPushoverNotification(any(), any()) }
    }

    @Test
    fun testReminderNotExecutedIfNotActive() = runTest {
        val fire = Instant.now().plus(15, ChronoUnit.MINUTES).toEpochMilli()
        val reminder = AdhocReminder("sc1", "e1",
            listOf(NotificationMethod.WEB, NotificationMethod.PUSHOVER), "message", fire,
            ZoneId.systemDefault().id, ReminderStatus.ACTIVE, 1234, 1234)

        every { reminderService.isActive(reminder.reminderId) } returns false
        val worker = createWorker(coroutineContext)
        val send = worker.worker()
        send.send(ReminderWorkerRequest(reminder, CrudType.CREATE))

        advanceTimeBy(TimeUnit.MINUTES.toMillis(16))
        send.close()
        worker.cancelAll()

        coVerify(exactly = 0) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 0) { notifyService.sendPushoverNotification(any(), any()) }
        verify(exactly = 1) { reminderService.isActive(reminder.reminderId) }
        verify(exactly = 0) { reminderService.updateReminderStatus(reminder.reminderId, ReminderStatus.COMPLETED) }
    }

    @Test
    fun testRecurringNotExecutedIfNotActive() = runTest {
        val reminder = RecurringReminder("sc1", "e1",
            listOf(NotificationMethod.WEB, NotificationMethod.PUSHOVER), "message", "every 3 hours",
            ZoneId.systemDefault().id, ReminderStatus.ACTIVE, 1234, 1234)

        every { reminderService.isActive(reminder.reminderId) } returns false
        val worker = createWorker(coroutineContext)
        val send = worker.worker()
        send.send(ReminderWorkerRequest(reminder, CrudType.CREATE))

        advanceTimeBy(TimeUnit.MINUTES.toMillis(185))
        send.close()
        worker.cancelAll()

        coVerify(exactly = 0) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 0) { notifyService.sendPushoverNotification(any(), any()) }
        verify(exactly = 1) { reminderService.isActive(reminder.reminderId) }
    }

    @Test
    fun testInitFromStart() = runTest {
        val tz = ZoneId.systemDefault()
        val fire = Instant.now().plus(15, ChronoUnit.MINUTES).toEpochMilli()
        val reminder = AdhocReminder("sc1", "e1",
            listOf(NotificationMethod.WEB), "message", fire,
            tz.id, ReminderStatus.ACTIVE, 1234, 1234)
        val recurring = RecurringReminder("sc1", "e1",
            listOf(NotificationMethod.WEB, NotificationMethod.PUSHOVER), "message", "every 3 hours",
            tz.id, ReminderStatus.ACTIVE, 1234, 1234)

        every { reminderService.getAllActiveReminders() } returns listOf(reminder, recurring)

        val worker = createWorker(coroutineContext)
        val send = worker.worker()

        advanceTimeBy(TimeUnit.MINUTES.toMillis(185))
        send.close()
        worker.cancelAll()

        coVerify(exactly = 1) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 1) { notifyService.accept(any(), recurring) }
        coVerify(exactly = 1) { notifyService.sendPushoverNotification(any(), any()) }
        verify(exactly = 2) { reminderService.isActive(reminder.reminderId) }
        verify(exactly = 1) { reminderService.updateReminderStatus(reminder.reminderId, ReminderStatus.COMPLETED) }
    }

    @Test
    fun testOnlyActiveRemindersStarted() = runTest {
        val tz = ZoneId.systemDefault()
        val fire = Instant.now().plus(15, ChronoUnit.MINUTES).toEpochMilli()
        val active = AdhocReminder("sc1", "e1",
            listOf(NotificationMethod.WEB), "message", fire,
            tz.id, ReminderStatus.ACTIVE, 1234, 1234)
        val disabled = AdhocReminder("sc2", "e1",
            listOf(NotificationMethod.WEB), "message", fire,
            tz.id, ReminderStatus.DISABLED, 1234, 1234)
        val completed = AdhocReminder("sc3", "e1",
            listOf(NotificationMethod.WEB), "message", fire,
            tz.id, ReminderStatus.COMPLETED, 1234, 1234)

        val worker = createWorker(coroutineContext)
        val send = worker.worker()
        send.send(ReminderWorkerRequest(active, CrudType.CREATE))
        send.send(ReminderWorkerRequest(disabled, CrudType.CREATE))
        send.send(ReminderWorkerRequest(completed, CrudType.CREATE))

        advanceTimeBy(TimeUnit.MINUTES.toMillis(185))
        send.close()
        worker.cancelAll()

        coVerify(exactly = 1) { notifyService.accept(any(), active) }
        coVerify(exactly = 0) { notifyService.accept(any(), disabled) }
        coVerify(exactly = 0) { notifyService.accept(any(), completed) }
        verify(exactly = 1) { reminderService.updateReminderStatus(active.reminderId, ReminderStatus.COMPLETED) }
    }

    @Test
    fun testUpdateReminder() = runTest {
        val tz = ZoneId.systemDefault()
        val fire = Instant.now().plus(2, ChronoUnit.HOURS).toEpochMilli()
        val reminder = AdhocReminder("sc1", "e1",
            listOf(NotificationMethod.WEB), "message", fire, tz.id,ReminderStatus.ACTIVE, 1234, 1234)

        val worker = createWorker(coroutineContext)
        val send = worker.worker()
        send.send(ReminderWorkerRequest(reminder, CrudType.CREATE))

        val updatedReminder = reminder.copy(interval = reminder.interval + 1800000) // + 30 mins

        send.send(ReminderWorkerRequest(updatedReminder, CrudType.UPDATE))

        advanceTimeBy(TimeUnit.MINUTES.toMillis(125))
        coVerify(exactly = 0) { notifyService.accept(any(), reminder) }

        advanceTimeBy(TimeUnit.MINUTES.toMillis(30))
        send.close()
        worker.cancelAll()

        coVerify(exactly = 1) { notifyService.accept(any(), updatedReminder) }
        coVerify(exactly = 1) { reminderService.updateReminderStatus(reminder.reminderId, ReminderStatus.COMPLETED) }
    }

    @Test
    fun testUpdateReminderToDisabled() = runTest {
        val tz = ZoneId.systemDefault()
        val fire = Instant.now().plus(2, ChronoUnit.HOURS).toEpochMilli()
        val reminder = AdhocReminder("sc1", "e1",
            listOf(NotificationMethod.WEB), "message", fire, tz.id,ReminderStatus.ACTIVE, 1234, 1234)

        val worker = createWorker(coroutineContext)
        val send = worker.worker()
        send.send(ReminderWorkerRequest(reminder, CrudType.CREATE))

        val updatedReminder = reminder.copy(status = ReminderStatus.DISABLED)

        send.send(ReminderWorkerRequest(updatedReminder, CrudType.UPDATE))

        advanceTimeBy(TimeUnit.MINUTES.toMillis(125))

        send.close()
        worker.cancelAll()

        coVerify(exactly = 0) { notifyService.accept(any(), updatedReminder) }
        coVerify(exactly = 0) { reminderService.updateReminderStatus(reminder.reminderId, ReminderStatus.COMPLETED) }
    }

    @Test
    fun testDeleteReminder() = runTest {
        val tz = ZoneId.systemDefault()
        val fire = Instant.now().plus(2, ChronoUnit.HOURS).toEpochMilli()
        val reminder = AdhocReminder("sc1", "e1",
            listOf(NotificationMethod.WEB), "message", fire, tz.id, ReminderStatus.ACTIVE, 1234, 1234)

        val worker = createWorker(coroutineContext)
        val send = worker.worker()

        send.send(ReminderWorkerRequest(reminder, CrudType.CREATE))
        send.send(ReminderWorkerRequest(reminder, CrudType.DELETE))

        advanceTimeBy(TimeUnit.MINUTES.toMillis(125))
        send.close()
        worker.cancelAll()

        coVerify(exactly = 0) { notifyService.accept(any(), reminder) }
        coVerify(exactly = 0) { notifyService.sendEmail(any(), any()) }
        coVerify(exactly = 0) { notifyService.sendPushoverNotification(any(), any()) }
    }

    private fun createWorker(context: CoroutineContext) = ReminderWorker(reminderService, entryService, notifyService, entryAuditService)
        .apply { runner = context }
}
