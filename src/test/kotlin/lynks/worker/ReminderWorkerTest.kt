package lynks.worker

import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import lynks.common.EntryId
import lynks.common.NotificationId
import lynks.common.ReminderId
import lynks.entry.EntryService
import lynks.notify.Notification
import lynks.notify.NotificationMethod
import lynks.notify.NotificationType
import lynks.notify.NotifyService
import lynks.reminder.*
import lynks.util.TEST_USER
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

    @BeforeEach
    fun before() {
        every { reminderService.getAllActiveReminders() } returns emptyList()
        every { reminderService.updateReminderStatus(any(), any()) } returns 1
        every { entryService.get(TEST_USER, EntryId("e1")) } returns null
        coEvery { notifyService.create(TEST_USER, any()) } returns Notification(
            NotificationId("n1"), NotificationType.DISCUSSIONS, "found", false, dateCreated = Instant.now()
        )
        coEvery { notifyService.sendJoltNotification(TEST_USER, any(), any()) } just Runs
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
        val reminder = AdhocReminder(ReminderId("sc1"), EntryId("e1"),
            listOf(NotificationMethod.PUSH), "message1", fire, tz.id, ReminderStatus.ACTIVE,
            Instant.EPOCH, Instant.EPOCH)
        val reminder2 = AdhocReminder(ReminderId("sc2"), EntryId("e1"),
            listOf(NotificationMethod.PUSH, NotificationMethod.JOLT), "message2", fire2,
            tz.id, ReminderStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH)

        val worker = createWorker(coroutineContext)
        val send = worker.worker()
        send.send(ReminderWorkerRequest(TEST_USER, reminder, CrudType.CREATE))
        send.send(ReminderWorkerRequest(TEST_USER, reminder2, CrudType.CREATE))

        advanceTimeBy(TimeUnit.MINUTES.toMillis(14))
        coVerify(exactly = 0) { notifyService.create(TEST_USER, coMatch { it.message == reminder.message }) }
        coVerify(exactly = 0) { notifyService.create(TEST_USER, coMatch { it.message == reminder2.message }) }
        coVerify(exactly = 0) { notifyService.sendJoltNotification(TEST_USER, any(), any()) }

        advanceTimeBy(TimeUnit.MINUTES.toMillis(1))
        coVerify(exactly = 1) { notifyService.create(TEST_USER, coMatch { it.message == reminder.message }) }
        coVerify(exactly = 0) { notifyService.create(TEST_USER, coMatch { it.message == reminder2.message }) }
        coVerify(exactly = 0) { notifyService.sendJoltNotification(TEST_USER, any(), any()) }

        advanceTimeBy(TimeUnit.MINUTES.toMillis(35))
        send.close()
        worker.cancelAll()

        coVerify(exactly = 1) { notifyService.create(TEST_USER, coMatch { it.message == reminder.message }) }
        coVerify(exactly = 1) { notifyService.create(TEST_USER, coMatch { it.message == reminder2.message }) }
        coVerify(exactly = 1) { notifyService.sendJoltNotification(TEST_USER, any(), any()) }
        coVerify(exactly = 1) { reminderService.updateReminderStatus(reminder.reminderId, ReminderStatus.COMPLETED) }
        coVerify(exactly = 1) { reminderService.updateReminderStatus(reminder2.reminderId, ReminderStatus.COMPLETED) }
    }

    @Test
    fun testSingleReminderDifferentTimezone() = runTest {
        val tz = ZoneId.of("Asia/Singapore")
        val fire = Instant.now().plus(2, ChronoUnit.HOURS).toEpochMilli()
        val fire2 = Instant.now().plus(2, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES).toEpochMilli()
        val reminder = AdhocReminder(ReminderId("sc1"), EntryId("e1"),
            listOf(NotificationMethod.PUSH, NotificationMethod.JOLT), "message1", fire, tz.id,
            ReminderStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH)
        val reminder2 = AdhocReminder(ReminderId("sc2"), EntryId("e1"),
            listOf(NotificationMethod.PUSH), "message2", fire2,
            tz.id, ReminderStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH)

        val worker = createWorker(coroutineContext)
        val send = worker.worker()
        send.send(ReminderWorkerRequest(TEST_USER, reminder, CrudType.CREATE))
        send.send(ReminderWorkerRequest(TEST_USER, reminder2, CrudType.CREATE))

        advanceTimeBy(TimeUnit.MINUTES.toMillis(118))
        coVerify(exactly = 0) { notifyService.create(TEST_USER, coMatch { it.message == reminder.message }) }
        coVerify(exactly = 0) { notifyService.create(TEST_USER, coMatch { it.message == reminder2.message }) }
        coVerify(exactly = 0) { notifyService.sendJoltNotification(TEST_USER, any(), any()) }

        advanceTimeBy(TimeUnit.MINUTES.toMillis(2))
        coVerify(exactly = 1) { notifyService.create(TEST_USER, coMatch { it.message == reminder.message }) }
        coVerify(exactly = 1) { notifyService.sendJoltNotification(TEST_USER, any(), any()) }
        coVerify(exactly = 0) { notifyService.create(TEST_USER, coMatch { it.message == reminder2.message }) }

        advanceTimeBy(TimeUnit.MINUTES.toMillis(35))
        send.close()
        worker.cancelAll()

        coVerify(exactly = 1) { notifyService.create(TEST_USER, coMatch { it.message == reminder.message }) }
        coVerify(exactly = 1) { notifyService.sendJoltNotification(TEST_USER, any(), any()) }
        coVerify(exactly = 1) { reminderService.updateReminderStatus(reminder.reminderId, ReminderStatus.COMPLETED) }
        coVerify(exactly = 1) { notifyService.create(TEST_USER, coMatch { it.message == reminder2.message }) }
        coVerify(exactly = 1) { reminderService.updateReminderStatus(reminder2.reminderId, ReminderStatus.COMPLETED) }
    }

    @Test
    fun testRecurringReminderSameTimezone() = runTest {
        val tz = ZoneId.systemDefault()
        val schedule = IntervalSchedule(30, IntervalUnit.MINUTES)
        val reminder = RecurringReminder(ReminderId("sc2"), EntryId("e1"),
            listOf(NotificationMethod.PUSH, NotificationMethod.JOLT), "message", schedule,
            tz.id, ReminderStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH)

        val worker = createWorker(coroutineContext)
        val send = worker.worker()
        send.send(ReminderWorkerRequest(TEST_USER, reminder, CrudType.CREATE))

        // intervals align to the clock, so the first fire is somewhere in the next half hour
        val untilFirst = ZonedDateTime.now(tz).until(schedule.next(ZonedDateTime.now(tz))!!, ChronoUnit.MILLIS)
        advanceTimeBy(untilFirst / 2)
        coVerify(exactly = 0) { notifyService.create(TEST_USER, coMatch { it.message == reminder.message }) }
        coVerify(exactly = 0) { notifyService.sendJoltNotification(TEST_USER, any(), any()) }

        advanceTimeBy(untilFirst / 2 + 200)
        coVerify(exactly = 1) { notifyService.create(TEST_USER, coMatch { it.message == reminder.message }) }

        advanceTimeBy(TimeUnit.MINUTES.toMillis(150))
        coVerify(exactly = 6) { notifyService.create(TEST_USER, coMatch { it.message == reminder.message }) }
        coVerify(exactly = 6) { notifyService.sendJoltNotification(TEST_USER, any(), any()) }

        advanceTimeBy(TimeUnit.MINUTES.toMillis(60))
        coVerify(exactly = 8) { notifyService.create(TEST_USER, coMatch { it.message == reminder.message }) }

        advanceTimeBy(TimeUnit.MINUTES.toMillis(120))
        send.close()
        worker.cancelAll()

        coVerify(exactly = 12) { notifyService.create(TEST_USER, coMatch { it.message == reminder.message }) }
        coVerify(exactly = 12) { notifyService.sendJoltNotification(TEST_USER, any(), any()) }
    }

    @Test
    fun testRecurringReminderDifferentTimezone() = runTest {
        val tz = ZoneId.of("Asia/Singapore")
        val reminder = RecurringReminder(ReminderId("sc1"), EntryId("e1"),
            listOf(NotificationMethod.PUSH), "message", CalendarSchedule(LocalTime.of(6, 0)),
            tz.id, ReminderStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH)

        val worker = createWorker(coroutineContext)
        val send = worker.worker()
        send.send(ReminderWorkerRequest(TEST_USER, reminder, CrudType.CREATE))

        val day = LocalDateTime.now(tz).let {
            if (it.hour >= 6) it.plusDays(1)
            else it
        }
        val fireDate = ZonedDateTime.of(day.toLocalDate(), LocalTime.of(6, 0), tz)

        val until = ZonedDateTime.now().until(fireDate, ChronoUnit.MILLIS)

        // 200ms buffer
        advanceTimeBy(TimeUnit.MILLISECONDS.toMillis((until / 2) + 200))
        coVerify(exactly = 0) { notifyService.create(TEST_USER, coMatch { it.message == reminder.message }) }

        advanceTimeBy(TimeUnit.MILLISECONDS.toMillis((until / 2) + 200))
        send.close()
        worker.cancelAll()

        coVerify(exactly = 1) { notifyService.create(TEST_USER, coMatch { it.message == reminder.message }) }
        coVerify(exactly = 0) { notifyService.sendJoltNotification(TEST_USER, any(), any()) }
    }

    @Test
    fun testReminderNotExecutedIfNotActive() = runTest {
        val fire = Instant.now().plus(15, ChronoUnit.MINUTES).toEpochMilli()
        val reminder = AdhocReminder(ReminderId("sc1"), EntryId("e1"),
            listOf(NotificationMethod.PUSH, NotificationMethod.JOLT), "message", fire,
            ZoneId.systemDefault().id, ReminderStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH)

        every { reminderService.isActive(reminder.reminderId) } returns false
        val worker = createWorker(coroutineContext)
        val send = worker.worker()
        send.send(ReminderWorkerRequest(TEST_USER, reminder, CrudType.CREATE))

        advanceTimeBy(TimeUnit.MINUTES.toMillis(16))
        send.close()
        worker.cancelAll()

        coVerify(exactly = 0) { notifyService.create(TEST_USER, coMatch { it.message == reminder.message }) }
        coVerify(exactly = 0) { notifyService.sendJoltNotification(TEST_USER, any(), any()) }
        verify(exactly = 1) { reminderService.isActive(reminder.reminderId) }
        verify(exactly = 0) { reminderService.updateReminderStatus(reminder.reminderId, ReminderStatus.COMPLETED) }
    }

    @Test
    fun testRecurringNotExecutedIfNotActive() = runTest {
        val reminder = RecurringReminder(ReminderId("sc1"), EntryId("e1"),
            listOf(NotificationMethod.PUSH, NotificationMethod.JOLT), "message", IntervalSchedule(3, IntervalUnit.HOURS),
            ZoneId.systemDefault().id, ReminderStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH)

        every { reminderService.isActive(reminder.reminderId) } returns false
        val worker = createWorker(coroutineContext)
        val send = worker.worker()
        send.send(ReminderWorkerRequest(TEST_USER, reminder, CrudType.CREATE))

        advanceTimeBy(TimeUnit.MINUTES.toMillis(185))
        send.close()
        worker.cancelAll()

        coVerify(exactly = 0) { notifyService.create(TEST_USER, coMatch { it.message == reminder.message }) }
        coVerify(exactly = 0) { notifyService.sendJoltNotification(TEST_USER, any(), any()) }
        verify(exactly = 1) { reminderService.isActive(reminder.reminderId) }
    }

    @Test
    fun testInitFromStart() = runTest {
        val tz = ZoneId.systemDefault()
        val fire = Instant.now().plus(15, ChronoUnit.MINUTES).toEpochMilli()
        val reminder = AdhocReminder(ReminderId("sc1"), EntryId("e1"),
            listOf(NotificationMethod.PUSH), "message1", fire,
            tz.id, ReminderStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH)
        val recurring = RecurringReminder(ReminderId("sc1"), EntryId("e1"),
            listOf(NotificationMethod.PUSH, NotificationMethod.JOLT), "message2", IntervalSchedule(3, IntervalUnit.HOURS),
            tz.id, ReminderStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH)

        every { reminderService.getAllActiveReminders() } returns listOf(TEST_USER to reminder, TEST_USER to recurring)

        val worker = createWorker(coroutineContext)
        val send = worker.worker()

        advanceTimeBy(TimeUnit.MINUTES.toMillis(185))
        send.close()
        worker.cancelAll()

        coVerify(exactly = 1) { notifyService.create(TEST_USER, coMatch { it.message == reminder.message }) }
        coVerify(exactly = 1) { notifyService.create(TEST_USER, coMatch { it.message == recurring.message }) }
        coVerify(exactly = 1) { notifyService.sendJoltNotification(TEST_USER, any(), any()) }
        verify(exactly = 2) { reminderService.isActive(reminder.reminderId) }
        verify(exactly = 1) { reminderService.updateReminderStatus(reminder.reminderId, ReminderStatus.COMPLETED) }
    }

    @Test
    fun testOnlyActiveRemindersStarted() = runTest {
        val tz = ZoneId.systemDefault()
        val fire = Instant.now().plus(15, ChronoUnit.MINUTES).toEpochMilli()
        val active = AdhocReminder(ReminderId("sc1"), EntryId("e1"),
            listOf(NotificationMethod.PUSH), "message1", fire,
            tz.id, ReminderStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH)
        val disabled = AdhocReminder(ReminderId("sc2"), EntryId("e1"),
            listOf(NotificationMethod.PUSH), "message2", fire,
            tz.id, ReminderStatus.DISABLED, Instant.EPOCH, Instant.EPOCH)
        val completed = AdhocReminder(ReminderId("sc3"), EntryId("e1"),
            listOf(NotificationMethod.PUSH), "message3", fire,
            tz.id, ReminderStatus.COMPLETED, Instant.EPOCH, Instant.EPOCH)

        val worker = createWorker(coroutineContext)
        val send = worker.worker()
        send.send(ReminderWorkerRequest(TEST_USER, active, CrudType.CREATE))
        send.send(ReminderWorkerRequest(TEST_USER, disabled, CrudType.CREATE))
        send.send(ReminderWorkerRequest(TEST_USER, completed, CrudType.CREATE))

        advanceTimeBy(TimeUnit.MINUTES.toMillis(185))
        send.close()
        worker.cancelAll()

        coVerify(exactly = 1) { notifyService.create(TEST_USER, coMatch { it.message == active.message }) }
        coVerify(exactly = 0) { notifyService.create(TEST_USER, coMatch { it.message == disabled.message }) }
        coVerify(exactly = 0) { notifyService.create(TEST_USER, coMatch { it.message == completed.message }) }
        verify(exactly = 1) { reminderService.updateReminderStatus(active.reminderId, ReminderStatus.COMPLETED) }
    }

    @Test
    fun testUpdateReminder() = runTest {
        val tz = ZoneId.systemDefault()
        val fire = Instant.now().plus(2, ChronoUnit.HOURS).toEpochMilli()
        val reminder = AdhocReminder(ReminderId("sc1"), EntryId("e1"),
            listOf(NotificationMethod.PUSH), "message", fire, tz.id, ReminderStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH
        )

        val worker = createWorker(coroutineContext)
        val send = worker.worker()
        send.send(ReminderWorkerRequest(TEST_USER, reminder, CrudType.CREATE))

        val updatedReminder = reminder.copy(fireAt = reminder.fireAt + 1800000) // + 30 mins

        send.send(ReminderWorkerRequest(TEST_USER, updatedReminder, CrudType.UPDATE))

        advanceTimeBy(TimeUnit.MINUTES.toMillis(125))
        coVerify(exactly = 0) { notifyService.create(TEST_USER, coMatch { it.message == reminder.message }) }

        advanceTimeBy(TimeUnit.MINUTES.toMillis(30))
        send.close()
        worker.cancelAll()

        coVerify(exactly = 1) { notifyService.create(TEST_USER, coMatch { it.message == updatedReminder.message }) }
        coVerify(exactly = 1) { reminderService.updateReminderStatus(reminder.reminderId, ReminderStatus.COMPLETED) }
    }

    @Test
    fun testUpdateReminderToDisabled() = runTest {
        val tz = ZoneId.systemDefault()
        val fire = Instant.now().plus(2, ChronoUnit.HOURS).toEpochMilli()
        val reminder = AdhocReminder(ReminderId("sc1"), EntryId("e1"),
            listOf(NotificationMethod.PUSH), "message", fire, tz.id, ReminderStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH
        )

        val worker = createWorker(coroutineContext)
        val send = worker.worker()
        send.send(ReminderWorkerRequest(TEST_USER, reminder, CrudType.CREATE))

        val updatedReminder = reminder.copy(status = ReminderStatus.DISABLED)

        send.send(ReminderWorkerRequest(TEST_USER, updatedReminder, CrudType.UPDATE))

        advanceTimeBy(TimeUnit.MINUTES.toMillis(125))

        send.close()
        worker.cancelAll()

        coVerify(exactly = 0) { notifyService.create(TEST_USER, coMatch { it.message == updatedReminder.message }) }
        coVerify(exactly = 0) { reminderService.updateReminderStatus(reminder.reminderId, ReminderStatus.COMPLETED) }
    }

    @Test
    fun testDeleteReminder() = runTest {
        val tz = ZoneId.systemDefault()
        val fire = Instant.now().plus(2, ChronoUnit.HOURS).toEpochMilli()
        val reminder = AdhocReminder(ReminderId("sc1"), EntryId("e1"),
            listOf(NotificationMethod.PUSH), "message", fire, tz.id, ReminderStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH
        )

        val worker = createWorker(coroutineContext)
        val send = worker.worker()

        send.send(ReminderWorkerRequest(TEST_USER, reminder, CrudType.CREATE))
        send.send(ReminderWorkerRequest(TEST_USER, reminder, CrudType.DELETE))

        advanceTimeBy(TimeUnit.MINUTES.toMillis(125))
        send.close()
        worker.cancelAll()

        coVerify(exactly = 0) { notifyService.create(TEST_USER, coMatch { it.message == reminder.message }) }
        coVerify(exactly = 0) { notifyService.sendJoltNotification(TEST_USER, any(), any()) }
    }

    @Test
    fun testUpdateCancelsReminderLaunchedAtStartup() = runTest {
        val tz = ZoneId.systemDefault()
        val fire = Instant.now().plus(15, ChronoUnit.MINUTES).toEpochMilli()
        val reminder = AdhocReminder(
            ReminderId("sc1"), EntryId("e1"),
            listOf(NotificationMethod.PUSH), "original", fire, tz.id, ReminderStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH
        )
        val updatedReminder = reminder.copy(
            message = "updated",
            fireAt = Instant.now().plus(45, ChronoUnit.MINUTES).toEpochMilli()
        )
        every { reminderService.getAllActiveReminders() } returns listOf(TEST_USER to reminder)

        val worker = createWorker(coroutineContext)
        val send = worker.worker()
        send.send(ReminderWorkerRequest(TEST_USER, updatedReminder, CrudType.UPDATE))

        advanceTimeBy(TimeUnit.MINUTES.toMillis(50))
        send.close()
        worker.cancelAll()

        coVerify(exactly = 0) { notifyService.create(TEST_USER, coMatch { it.message == reminder.message }) }
        coVerify(exactly = 1) { notifyService.create(TEST_USER, coMatch { it.message == updatedReminder.message }) }
    }

    private fun TestScope.createWorker(context: CoroutineContext) =
        ReminderWorker(reminderService, notifyService, virtualClock()).apply { runner = context }

    // follows virtual time, so the schedule and the delays it produces advance together
    private fun TestScope.virtualClock(): Clock {
        val start = Instant.now()
        return object : Clock() {
            override fun instant(): Instant = start.plusMillis(testScheduler.currentTime)
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = this
        }
    }
}
