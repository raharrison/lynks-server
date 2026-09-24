package lynks.service

import io.mockk.mockk
import io.mockk.verify
import lynks.common.*
import lynks.common.exception.InvalidModelException
import lynks.common.page.PageRequest
import lynks.notify.NotificationMethod
import lynks.reminder.*
import lynks.util.TEST_USER
import lynks.util.createDummyEntry
import lynks.util.createDummyReminder
import lynks.worker.WorkerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class ReminderServiceTest : DatabaseTest() {

    private val workerRegistry = mockk<WorkerRegistry>(relaxUnitFun = true)
    private val reminderService = ReminderService(workerRegistry)
    private val tz = ZoneId.systemDefault().id
    private val daily = CalendarSchedule(LocalTime.of(9, 0))

    @BeforeEach
    fun insertEntry() {
        createDummyEntry("e1", "title", "content", EntryType.NOTE)
        Thread.sleep(10)
        createDummyEntry("e2", "title", "content", EntryType.NOTE)
    }

    @Test
    fun testAddNewReminder() {
        val reminder1 = NewReminder(null, EntryId("e1"), ReminderType.ADHOC,
            listOf(NotificationMethod.JOLT, NotificationMethod.PUSH), "message", 100, null, tz, ReminderStatus.ACTIVE
        )
        val reminder2 = NewReminder(null, EntryId("e1"), ReminderType.RECURRING,
            listOf(NotificationMethod.JOLT, NotificationMethod.PUSH), "message2", null, daily, tz, ReminderStatus.DISABLED
        )
        val saved1 = reminderService.addReminder(TEST_USER, reminder1)
        val saved2 = reminderService.addReminder(TEST_USER, reminder2)
        val retrieved = reminderService.get(TEST_USER, saved1.reminderId)
        val retrieved2 = reminderService.get(TEST_USER, saved2.reminderId)
        assertThat(retrieved).isEqualTo(saved1)
        assertThat(retrieved?.tz).isEqualTo(reminder1.tz)
        assertThat(retrieved2).isEqualTo(saved2)
        assertThat(retrieved2?.tz).isEqualTo(reminder2.tz)
        assertThat(retrieved?.type).isEqualTo(ReminderType.ADHOC)
        assertThat(retrieved2?.type).isEqualTo(ReminderType.RECURRING)
        assertThat((retrieved as AdhocReminder).fireAt).isEqualTo(reminder1.fireAt)
        assertThat((retrieved2 as RecurringReminder).schedule).isEqualTo(reminder2.schedule)
        assertThat(retrieved?.message).isEqualTo(reminder1.message)
        assertThat(retrieved?.entryTitle).isEqualTo("title")
        assertThat(retrieved?.entryType).isEqualTo(EntryType.NOTE)
        assertThat(retrieved2?.message).isEqualTo(reminder2.message)
        assertThat(retrieved?.notifyMethods).containsExactly(NotificationMethod.JOLT, NotificationMethod.PUSH)
        assertThat(retrieved2?.notifyMethods).containsExactly(NotificationMethod.JOLT, NotificationMethod.PUSH)
        assertThat(retrieved?.dateCreated).isEqualTo(retrieved?.dateUpdated)
        assertThat(retrieved2?.dateCreated).isEqualTo(retrieved2?.dateUpdated)
        assertThat(retrieved?.status).isEqualTo(ReminderStatus.ACTIVE)
        assertThat(retrieved2?.status).isEqualTo(ReminderStatus.DISABLED)
        verify(exactly = 2) { workerRegistry.acceptReminderWork(any()) }
    }

    @Test
    fun testAddReminderInvalidTimeZone() {
        assertThrows<InvalidModelException> {
            reminderService.addReminder(
                TEST_USER, NewReminder(
                    null, EntryId("e1"), ReminderType.ADHOC,
                    listOf(NotificationMethod.PUSH), "message", 100, null, "invalid", ReminderStatus.ACTIVE
            )
            )
        }
    }

    @Test
    fun testAddReminderGivenIdDoesNotPerformUpdate() {
        val rem = reminderService.addReminder(
            TEST_USER, NewReminder(
                null, EntryId("e1"), ReminderType.ADHOC,
                listOf(NotificationMethod.JOLT), "message", 100, null, tz, ReminderStatus.ACTIVE
        )
        )
        val res = reminderService.addReminder(
            TEST_USER, NewReminder(
                rem.reminderId, EntryId("e1"), ReminderType.RECURRING,
                listOf(NotificationMethod.PUSH), "message", null, daily, tz, ReminderStatus.COMPLETED
        )
        )
        assertThat(rem.reminderId).isNotEqualTo(res.reminderId)
        assertThat(res.entryId).isEqualTo(EntryId("e1"))
        assertThat(res.type).isEqualTo(ReminderType.RECURRING)
        assertThat(res.message).isEqualTo("message")
        assertThat((res as RecurringReminder).schedule).isEqualTo(daily)
        assertThat(res.tz).isEqualTo(tz)
        assertThat(rem.dateCreated).isEqualTo(rem.dateUpdated)
        assertThat(res.dateCreated).isEqualTo(res.dateUpdated)
        assertThat(res.notifyMethods).containsExactly(NotificationMethod.PUSH)
        assertThat(rem.status).isEqualTo(ReminderStatus.ACTIVE)
        assertThat(res.status).isEqualTo(ReminderStatus.COMPLETED)
        assertThat(reminderService.getAllReminders(TEST_USER).content).hasSize(2)
        assertThat(reminderService.get(TEST_USER, res.reminderId)).isEqualTo(res)
        assertThat(reminderService.get(TEST_USER, rem.reminderId)).isEqualTo(rem)
        verify(exactly = 2) { workerRegistry.acceptReminderWork(any()) }
    }

    @Test
    fun testAddReminderNoEntry() {
        assertThrows<InvalidModelException> {
            reminderService.addReminder(
                TEST_USER, NewReminder(
                    null, EntryId("nothing"), ReminderType.ADHOC,
                    listOf(NotificationMethod.JOLT), "message", 100, null, tz, ReminderStatus.ACTIVE
            )
            )
        }
    }

    @Test
    fun testGetAllReminders() {
        reminderService.add(reminder(ReminderId("sid"), EntryId("e1"), ReminderType.ADHOC,
            listOf(NotificationMethod.PUSH), "message", 100, ReminderStatus.ACTIVE
        )
        )
        reminderService.add(reminder(ReminderId("sid2"), EntryId("e1"), ReminderType.RECURRING,
            listOf(NotificationMethod.JOLT), "message", 200, ReminderStatus.COMPLETED
        )
        )
        reminderService.add(reminder(ReminderId("sid3"), EntryId("e2"), ReminderType.RECURRING,
            listOf(NotificationMethod.JOLT, NotificationMethod.PUSH), "message", 300, ReminderStatus.DISABLED
        )
        )

        val reminders = reminderService.getAllReminders(TEST_USER).content
        assertThat(reminders).hasSize(3)
        assertThat(reminders).extracting<ReminderId> { it.reminderId }
            .containsExactlyInAnyOrder(ReminderId("sid"), ReminderId("sid2"), ReminderId("sid3"))
        assertThat(reminders).extracting<EntryId> { it.entryId }
            .containsOnly(EntryId("e1"), EntryId("e2"))
        assertThat(reminders).extracting("type").containsOnly(ReminderType.RECURRING, ReminderType.ADHOC)
        assertThat(reminders).extracting("notifyMethods").containsExactlyInAnyOrder(
            listOf(NotificationMethod.PUSH), listOf(NotificationMethod.JOLT),
            listOf(NotificationMethod.JOLT, NotificationMethod.PUSH)
        )
        assertThat(reminders).extracting("message").containsOnly("message")
        assertThat(reminders).extracting("tz").containsOnly(tz)
        assertThat(reminders).extracting("status").containsExactlyInAnyOrder(ReminderStatus.ACTIVE,
            ReminderStatus.COMPLETED, ReminderStatus.DISABLED)
    }

    @Test
    fun testGetAllRemindersPagination() {
        reminderService.add(reminder(ReminderId("sid"), EntryId("e1"), ReminderType.ADHOC,
            listOf(NotificationMethod.PUSH), "message", 100, ReminderStatus.ACTIVE
        )
        )
        reminderService.add(reminder(ReminderId("sid2"), EntryId("e1"), ReminderType.RECURRING,
            listOf(NotificationMethod.JOLT), "message", 200, ReminderStatus.COMPLETED
        )
        )
        reminderService.add(reminder(ReminderId("sid3"), EntryId("e2"), ReminderType.RECURRING,
            listOf(NotificationMethod.JOLT), "message", 300, ReminderStatus.DISABLED
        )
        )

        val page1 = reminderService.getAllReminders(TEST_USER, PageRequest(page = 1, size = 2))
        assertThat(page1.total).isEqualTo(3)
        assertThat(page1.page).isEqualTo(1)
        assertThat(page1.size).isEqualTo(2)
        assertThat(page1.content).hasSize(2)

        val page2 = reminderService.getAllReminders(TEST_USER, PageRequest(page = 2, size = 2))
        assertThat(page2.total).isEqualTo(3)
        assertThat(page2.content).hasSize(1)
    }

    @Test
    fun testGetAllActiveReminders() {
        reminderService.add(reminder(ReminderId("sid"), EntryId("e1"), ReminderType.ADHOC,
            listOf(NotificationMethod.PUSH), "message", 100, ReminderStatus.COMPLETED
        )
        )
        reminderService.add(reminder(ReminderId("sid2"), EntryId("e1"), ReminderType.RECURRING,
            listOf(NotificationMethod.JOLT), "message", 200, ReminderStatus.ACTIVE
        )
        )
        reminderService.add(reminder(ReminderId("sid3"), EntryId("e2"), ReminderType.RECURRING,
            listOf(NotificationMethod.JOLT, NotificationMethod.PUSH), "message", 300, ReminderStatus.DISABLED
        )
        )

        val active = reminderService.getAllActiveReminders()
        assertThat(active).extracting<UserId> { it.first }.containsOnly(TEST_USER)
        val reminders = active.map { it.second }
        assertThat(reminders).hasSize(1)
        assertThat(reminders).extracting<ReminderId> { it.reminderId }
            .containsExactly(ReminderId("sid2"))
        assertThat(reminders).extracting("status").containsExactly(ReminderStatus.ACTIVE)
    }

    @Test
    fun testGetRemindersForEntry() {
        reminderService.add(reminder(ReminderId("sid"), EntryId("e1"), ReminderType.ADHOC,
            listOf(NotificationMethod.JOLT, NotificationMethod.PUSH), "message", 100, ReminderStatus.ACTIVE
        )
        )
        reminderService.add(reminder(ReminderId("sid2"), EntryId("e1"), ReminderType.RECURRING,
            listOf(NotificationMethod.JOLT), "message", 200, ReminderStatus.COMPLETED
        )
        )
        reminderService.add(reminder(ReminderId("sid3"), EntryId("e2"), ReminderType.RECURRING,
            listOf(NotificationMethod.PUSH), "message", 300, ReminderStatus.DISABLED
        )
        )

        val remsE1 = reminderService.getRemindersForEntry(TEST_USER, EntryId("e1"))
        assertThat(remsE1).hasSize(2).extracting<ReminderId> { it.reminderId }
            .containsExactlyInAnyOrder(ReminderId("sid"), ReminderId("sid2"))
        val remsE12 = reminderService.getRemindersForEntry(TEST_USER, EntryId("e2"))
        assertThat(remsE12).hasSize(1).extracting<ReminderId> { it.reminderId }
            .containsExactlyInAnyOrder(ReminderId("sid3"))
        assertThat(reminderService.getRemindersForEntry(TEST_USER, EntryId("nothing"))).isEmpty()
    }

    @Test
    fun testGetRemindersReturnsType() {
        val s1 = reminderService.add(reminder(ReminderId("sid"), EntryId("e1"), ReminderType.ADHOC,
            listOf(NotificationMethod.PUSH), "message", 100, ReminderStatus.DISABLED
        )
        )
        val s2 = reminderService.add(reminder(ReminderId("sid3"), EntryId("e2"), ReminderType.RECURRING,
            listOf(NotificationMethod.JOLT), "message", 300, ReminderStatus.ACTIVE
        )
        )
        assertThat(reminderService.get(TEST_USER, s1.reminderId)).isInstanceOf(Reminder::class.java)
        assertThat(reminderService.get(TEST_USER, s2.reminderId)).isInstanceOf(RecurringReminder::class.java)
    }

    @Test
    fun testUpdateReminderNoRow() {
        assertThat(
            reminderService.updateReminder(
                TEST_USER, NewReminder(
                    ReminderId("invalid"), EntryId("e1"), ReminderType.ADHOC,
                    listOf(NotificationMethod.PUSH), "message", 300, null, tz, ReminderStatus.ACTIVE
        )
        )
        ).isNull()
        verify(exactly = 0) { workerRegistry.acceptReminderWork(any()) }
    }

    @Test
    fun testUpdateReminderNoId() {
        val res = reminderService.updateReminder(
            TEST_USER, NewReminder(
                null, EntryId("e1"), ReminderType.ADHOC,
                listOf(NotificationMethod.JOLT, NotificationMethod.PUSH), "message", 300, null, tz, ReminderStatus.ACTIVE
        )
        )
        assertThat(res?.reminderId?.value).isNotBlank()
        assertThat(res?.entryId).isEqualTo(EntryId("e1"))
        assertThat(res?.type).isEqualTo(ReminderType.ADHOC)
        assertThat(res?.notifyMethods).contains(NotificationMethod.JOLT, NotificationMethod.PUSH)
        assertThat(res?.message).isEqualTo("message")
        assertThat((res as AdhocReminder?)?.fireAt).isEqualTo(300L)
        assertThat(res?.dateCreated).isEqualTo(res?.dateUpdated)
        assertThat(res?.status).isEqualTo(ReminderStatus.ACTIVE)
        assertThat(reminderService.get(TEST_USER, res!!.reminderId)).isEqualTo(res)
        assertThat(reminderService.getAllReminders(TEST_USER).content).hasSize(1)
        verify(exactly = 1) { workerRegistry.acceptReminderWork(any()) }
    }

    @Test
    fun testUpdateReminder() {
        val res1 = reminderService.addReminder(
            TEST_USER, NewReminder(
                null, EntryId("e1"), ReminderType.ADHOC,
                listOf(NotificationMethod.JOLT), "message", 100, null, tz, ReminderStatus.ACTIVE
        )
        )
        val res2 = reminderService.addReminder(
            TEST_USER, NewReminder(
                null, EntryId("e1"), ReminderType.RECURRING,
                listOf(NotificationMethod.PUSH), "message", null, daily, tz, ReminderStatus.DISABLED
        )
        )
        assertThat(reminderService.getAllReminders(TEST_USER).content).hasSize(2).extracting<ReminderId> { it.reminderId }
            .doesNotHaveDuplicates()

        val updated = reminderService.updateReminder(
            TEST_USER, NewReminder(
                res1.reminderId, EntryId("e1"), ReminderType.RECURRING,
                listOf(NotificationMethod.JOLT, NotificationMethod.PUSH), "message2", null, daily, tz, ReminderStatus.COMPLETED
        )
        )
        assertThat(updated?.entryId).isEqualTo(EntryId("e1"))
        assertThat(updated?.type).isEqualTo(ReminderType.RECURRING)
        assertThat(updated?.notifyMethods).containsExactly(NotificationMethod.JOLT, NotificationMethod.PUSH)
        assertThat(updated?.message).isEqualTo("message2")
        assertThat((updated as RecurringReminder?)?.schedule).isEqualTo(daily)
        assertThat(updated?.dateCreated).isBeforeOrEqualTo(updated?.dateUpdated)
        assertThat(updated?.status).isEqualTo(ReminderStatus.COMPLETED)
        assertThat(reminderService.get(TEST_USER, res1.reminderId)).isEqualTo(updated)

        // cannot update entryId
        val updated2 = reminderService.updateReminder(
            TEST_USER, NewReminder(
                res2.reminderId, EntryId("e2"), ReminderType.ADHOC,
                listOf(NotificationMethod.JOLT), "message3", 800, null, "America/New_York", ReminderStatus.ACTIVE
        )
        )
        assertThat(updated2?.entryId).isEqualTo(EntryId("e1"))
        assertThat(updated2?.type).isEqualTo(ReminderType.ADHOC)
        assertThat(updated2?.notifyMethods).containsOnly(NotificationMethod.JOLT)
        assertThat(updated2?.message).isEqualTo("message3")
        assertThat((updated2 as AdhocReminder?)?.fireAt).isEqualTo(800L)
        assertThat(updated2?.tz).isEqualTo("America/New_York")
        assertThat(updated2?.dateCreated).isNotEqualTo(updated2?.dateUpdated)
        assertThat(updated2?.status).isEqualTo(ReminderStatus.ACTIVE)
        assertThat(reminderService.get(TEST_USER, res2.reminderId)).isEqualTo(updated2)

        verify(exactly = 4) { workerRegistry.acceptReminderWork(any()) }
    }

    @Test
    fun testUpdateReminderInvalidTimeZone() {
        val res1 = reminderService.addReminder(
            TEST_USER, NewReminder(
                null, EntryId("e1"), ReminderType.ADHOC,
                listOf(NotificationMethod.JOLT), "message", 100, null, tz, ReminderStatus.ACTIVE
        )
        )
        assertThrows<InvalidModelException> {
            reminderService.updateReminder(
                TEST_USER, NewReminder(
                    res1.reminderId, EntryId("e1"), ReminderType.RECURRING,
                    listOf(NotificationMethod.PUSH), "message", null, daily, "invalid", ReminderStatus.DISABLED
            )
            )
        }
    }

    @Test
    fun testUpdateReminderStatus() {
        val res1 = reminderService.addReminder(
            TEST_USER, NewReminder(
                null, EntryId("e1"), ReminderType.ADHOC,
                listOf(NotificationMethod.JOLT), "message", 100, null, tz, ReminderStatus.ACTIVE
        )
        )
        val updated = reminderService.updateReminderStatus(res1.reminderId, ReminderStatus.COMPLETED)
        assertThat(updated).isOne()
        val retrieved = reminderService.get(TEST_USER, res1.reminderId)
        assertThat(retrieved?.status).isEqualTo(ReminderStatus.COMPLETED)
    }

    @Test
    fun testDeleteReminder() {
        val s1 = reminderService.add(reminder(ReminderId("sid"), EntryId("e2"), ReminderType.RECURRING,
            listOf(NotificationMethod.JOLT), "message", 300, ReminderStatus.ACTIVE
        )
        )
        assertThat(reminderService.getAllReminders(TEST_USER).content).hasSize(1)

        assertThat(reminderService.delete(TEST_USER, s1.reminderId)).isTrue()
        assertThat(reminderService.get(TEST_USER, s1.reminderId)).isNull()
        assertThat(reminderService.getAllReminders(TEST_USER).content).isEmpty()
        verify(exactly = 1) { workerRegistry.acceptReminderWork(any()) }
    }

    @Test
    fun testIsActive() {
        val res1 = reminderService.addReminder(
            TEST_USER, NewReminder(
                null, EntryId("e1"), ReminderType.ADHOC,
                listOf(NotificationMethod.JOLT), "elapsed", 100, null, tz, ReminderStatus.ACTIVE
        )
        )
        val res2 = reminderService.addReminder(
            TEST_USER, NewReminder(
                null, EntryId("e1"), ReminderType.RECURRING,
                listOf(NotificationMethod.PUSH), "elapsed", null, daily, tz, ReminderStatus.DISABLED
        )
        )
        assertThat(reminderService.isActive(res1.reminderId)).isTrue()
        assertThat(reminderService.isActive(res2.reminderId)).isFalse()
        assertThat(reminderService.isActive(ReminderId("invalid"))).isFalse()
        assertThat(reminderService.isActive(ReminderId(""))).isFalse()
    }

    @Test
    fun testPreviewSchedule() {
        val fires = reminderService.previewSchedule(CalendarSchedule(LocalTime.of(17, 0)), "Asia/Singapore")
        assertThat(fires).hasSize(5)
        assertThat(fires).allSatisfy {
            assertThat(it.atZoneSameInstant(ZoneId.of("Asia/Singapore")).toLocalTime()).isEqualTo(LocalTime.of(17, 0))
        }
        assertThat(fires).isSorted()
        assertThrows<InvalidModelException> { reminderService.previewSchedule(IntervalSchedule(0, IntervalUnit.HOURS), tz) }
        assertThrows<InvalidModelException> { reminderService.previewSchedule(daily, "invalid") }
    }

    @Test
    fun testAddReminderRejectsBadSpec() {
        assertThrows<InvalidModelException> {
            reminderService.addReminder(
                TEST_USER, NewReminder(
                    null, EntryId("e1"), ReminderType.RECURRING,
                    listOf(NotificationMethod.PUSH), tz = tz, status = ReminderStatus.ACTIVE
                )
            )
        }
        assertThrows<InvalidModelException> {
            reminderService.addReminder(
                TEST_USER, NewReminder(
                    null, EntryId("e1"), ReminderType.ADHOC,
                    listOf(NotificationMethod.PUSH), schedule = daily, tz = tz, status = ReminderStatus.ACTIVE
                )
            )
        }
        assertThrows<InvalidModelException> {
            reminderService.addReminder(
                TEST_USER, NewReminder(
                    null,
                    EntryId("e1"),
                    ReminderType.RECURRING,
                    listOf(NotificationMethod.PUSH),
                    schedule = CalendarSchedule(LocalTime.NOON, monthDays = setOf(30), months = setOf(2)),
                    tz = tz,
                    status = ReminderStatus.ACTIVE
                )
            )
        }
    }

    // seeds a row with a known id, bypassing the worker
    private fun ReminderService.add(reminder: Reminder): Reminder {
        createDummyReminder(
            reminder.reminderId.value, reminder.entryId.value, reminder.type, reminder.notifyMethods,
            reminder.message, when (reminder) {
                is AdhocReminder -> reminder.fireAt.toString()
                is RecurringReminder -> reminder.schedule.toSpec()
                else -> error("Unknown reminder $reminder")
            }, reminder.tz, reminder.status
        )
        return get(TEST_USER, reminder.reminderId)!!
    }

    private fun reminder(
        sid: ReminderId,
        eId: EntryId,
        type: ReminderType,
        notifyMethods: List<NotificationMethod> = listOf(NotificationMethod.JOLT),
                         message: String? = null, interval: Long = 0, status: ReminderStatus): Reminder {
        val time = Instant.now()
        return when (type) {
            ReminderType.ADHOC -> AdhocReminder(sid, entryId = eId, message = message, notifyMethods = notifyMethods,
                fireAt = interval, tz = this.tz, status = status, dateCreated = time, dateUpdated = time
            )
            ReminderType.RECURRING -> RecurringReminder(sid, entryId = eId, message = message, notifyMethods = notifyMethods,
                schedule = IntervalSchedule(interval.toInt(), IntervalUnit.MINUTES),
                tz = this.tz,
                status = status,
                dateCreated = time,
                dateUpdated = time
            )
        }
    }

}
