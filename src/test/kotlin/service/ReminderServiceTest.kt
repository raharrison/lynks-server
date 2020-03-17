package service

import common.DatabaseTest
import common.EntryType
import io.mockk.mockk
import io.mockk.verify
import notify.NotificationMethod
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import reminder.*
import util.createDummyEntry
import worker.WorkerRegistry
import java.sql.SQLException
import java.time.ZoneId

class ReminderServiceTest : DatabaseTest() {

    private val workerRegistry = mockk<WorkerRegistry>(relaxUnitFun = true)
    private val reminderService = ReminderService(workerRegistry)
    private val tz = ZoneId.systemDefault().id

    @BeforeEach
    fun insertEntry() {
        createDummyEntry("e1", "title", "content", EntryType.NOTE)
        Thread.sleep(10)
        createDummyEntry("e2", "title", "content", EntryType.NOTE)
    }

    @Test
    fun testAddNewReminderDirectly() {
        val reminder = reminderService.add(reminder("sid", "e1", ReminderType.ADHOC, NotificationMethod.EMAIL, "message", 100))
        assertThat(reminder.reminderId).isEqualTo("sid")
        assertThat(reminder.entryId).isEqualTo("e1")
        assertThat(reminder.spec).isEqualTo("100")
        assertThat(reminder.message).isEqualTo("message")
        assertThat(reminder.notifyMethod).isEqualTo(NotificationMethod.EMAIL)
        val retrieved = reminderService.get(reminder.reminderId)
        assertThat(retrieved).isEqualTo(reminder)
        verify(exactly = 1) { workerRegistry.acceptReminderWork(any()) }
    }

    @Test
    fun testAddNewReminder() {
        val reminder1 = NewReminder(null, "e1", ReminderType.ADHOC, NotificationMethod.BOTH, "message", "100", tz)
        val reminder2 = NewReminder(null, "e1", ReminderType.RECURRING, NotificationMethod.PUSH, "message2", "every", tz)
        val saved1 = reminderService.addReminder(reminder1)
        val saved2 = reminderService.addReminder(reminder2)
        val retrieved = reminderService.get(saved1.reminderId)
        val retrieved2 = reminderService.get(saved2.reminderId)
        assertThat(retrieved).isEqualTo(saved1)
        assertThat(retrieved?.tz).isEqualTo(reminder1.tz)
        assertThat(retrieved2).isEqualTo(saved2)
        assertThat(retrieved2?.tz).isEqualTo(reminder2.tz)
        assertThat(retrieved?.type).isEqualTo(ReminderType.ADHOC)
        assertThat(retrieved2?.type).isEqualTo(ReminderType.RECURRING)
        assertThat(retrieved?.spec).isEqualTo(reminder1.spec)
        assertThat(retrieved2?.spec).isEqualTo(reminder2.spec)
        assertThat(retrieved?.message).isEqualTo(reminder1.message)
        assertThat(retrieved2?.message).isEqualTo(reminder2.message)
        assertThat(retrieved?.notifyMethod).isEqualTo(NotificationMethod.BOTH)
        assertThat(retrieved2?.notifyMethod).isEqualTo(NotificationMethod.PUSH)
        verify(exactly = 2) { workerRegistry.acceptReminderWork(any()) }
    }

    @Test
    fun testAddReminderInvalidTimeZone() {
        assertThrows<IllegalArgumentException> {
            reminderService.addReminder(NewReminder(null, "e1", ReminderType.ADHOC, NotificationMethod.PUSH, "message", "100", "invalid"))
        }
    }

    @Test
    fun testAddReminderGivenIdDoesNotPerformUpdate() {
        val rem = reminderService.addReminder(NewReminder(null, "e1", ReminderType.ADHOC, NotificationMethod.BOTH, "message", "100", tz))
        val res = reminderService.addReminder(NewReminder(rem.reminderId, "e1", ReminderType.RECURRING, NotificationMethod.PUSH, "message", "200", tz))
        assertThat(rem.reminderId).isNotEqualTo(res.reminderId)
        assertThat(res.entryId).isEqualTo("e1")
        assertThat(res.type).isEqualTo(ReminderType.RECURRING)
        assertThat(res.message).isEqualTo("message")
        assertThat(res.spec).isEqualTo("200")
        assertThat(res.tz).isEqualTo(tz)
        assertThat(res.notifyMethod).isEqualTo(NotificationMethod.PUSH)
        assertThat(reminderService.getAllReminders()).hasSize(2)
        assertThat(reminderService.get(res.reminderId)).isEqualTo(res)
        assertThat(reminderService.get(rem.reminderId)).isEqualTo(rem)
        verify(exactly = 2) { workerRegistry.acceptReminderWork(any()) }
    }

    @Test
    fun testAddReminderNoEntry() {
        assertThrows<SQLException> {
            reminderService.add(reminder("sid", "nothing", ReminderType.ADHOC, NotificationMethod.PUSH, "message", 100))
        }
        assertThrows<SQLException> {
            reminderService.addReminder(NewReminder(null, "nothing", ReminderType.ADHOC, NotificationMethod.PUSH, "message", "100", tz))
        }
    }

    @Test
    fun testGetAllReminders() {
        reminderService.add(reminder("sid", "e1", ReminderType.ADHOC, NotificationMethod.PUSH, "message", 100))
        reminderService.add(reminder("sid2", "e1", ReminderType.RECURRING, NotificationMethod.EMAIL, "message", 200))
        reminderService.add(reminder("sid3", "e2", ReminderType.RECURRING, NotificationMethod.BOTH, "message", 300))

        val reminders = reminderService.getAllReminders()
        assertThat(reminders).hasSize(3)
        assertThat(reminders).extracting("reminderId").containsExactlyInAnyOrder("sid", "sid2", "sid3")
        assertThat(reminders).extracting("entryId").containsOnly("e1", "e2")
        assertThat(reminders).extracting("type").containsOnly(ReminderType.RECURRING, ReminderType.ADHOC)
        assertThat(reminders).extracting("notifyMethod").containsExactly(NotificationMethod.PUSH, NotificationMethod.EMAIL, NotificationMethod.BOTH)
        assertThat(reminders).extracting("message").containsOnly("message")
        assertThat(reminders).extracting("spec").doesNotContain("400")
        assertThat(reminders).extracting("tz").containsOnly(tz)
    }

    @Test
    fun testGetRemindersForEntry() {
        reminderService.add(reminder("sid", "e1", ReminderType.ADHOC, NotificationMethod.BOTH, "message", 100))
        reminderService.add(reminder("sid2", "e1", ReminderType.RECURRING, NotificationMethod.EMAIL, "message", 200))
        reminderService.add(reminder("sid3", "e2", ReminderType.RECURRING, NotificationMethod.PUSH, "message", 300))

        val remsE1 = reminderService.getRemindersForEntry("e1")
        assertThat(remsE1).hasSize(2).extracting("reminderId").containsExactlyInAnyOrder("sid", "sid2")
        val remsE12 = reminderService.getRemindersForEntry("e2")
        assertThat(remsE12).hasSize(1).extracting("reminderId").containsExactlyInAnyOrder("sid3")
        assertThat(reminderService.getRemindersForEntry("nothing")).isEmpty()
    }

    @Test
    fun testGetRemindersReturnsType() {
        val s1 = reminderService.add(reminder("sid", "e1", ReminderType.ADHOC, NotificationMethod.PUSH, "message", 100))
        val s2 = reminderService.add(reminder("sid3", "e2", ReminderType.RECURRING, NotificationMethod.EMAIL, "message", 300))
        assertThat(reminderService.get(s1.reminderId)).isInstanceOf(Reminder::class.java)
        assertThat(reminderService.get(s2.reminderId)).isInstanceOf(RecurringReminder::class.java)
    }

    @Test
    fun testUpdateReminderNoRow() {
        assertThat(reminderService.updateReminder(NewReminder("invalid", "e1", ReminderType.ADHOC, NotificationMethod.PUSH, "message", "300", tz))).isNull()
        verify(exactly = 0) { workerRegistry.acceptReminderWork(any()) }
    }

    @Test
    fun testUpdateReminderNoId() {
        val res = reminderService.updateReminder(NewReminder(null, "e1", ReminderType.ADHOC, NotificationMethod.EMAIL, "message", "300", tz))
        assertThat(res?.reminderId).isNotBlank()
        assertThat(res?.entryId).isEqualTo("e1")
        assertThat(res?.type).isEqualTo(ReminderType.ADHOC)
        assertThat(res?.notifyMethod).isEqualTo(NotificationMethod.EMAIL)
        assertThat(res?.message).isEqualTo("message")
        assertThat(res?.spec).isEqualTo("300")
        assertThat(reminderService.get(res!!.reminderId)).isEqualTo(res)
        assertThat(reminderService.getAllReminders()).hasSize(1)
        verify(exactly = 1) { workerRegistry.acceptReminderWork(any()) }
    }

    @Test
    fun testUpdateReminder() {
        val res1 = reminderService.addReminder(NewReminder(null, "e1", ReminderType.ADHOC, NotificationMethod.EMAIL, "message", "100", tz))
        val res2 = reminderService.addReminder(NewReminder(null, "e1", ReminderType.RECURRING, NotificationMethod.PUSH, "message", "200", tz))
        assertThat(reminderService.getAllReminders()).hasSize(2).extracting("reminderId").doesNotHaveDuplicates()

        val updated = reminderService.updateReminder(NewReminder(res1.reminderId, "e1", ReminderType.RECURRING, NotificationMethod.BOTH, "message2", "500", tz))
        assertThat(updated?.entryId).isEqualTo("e1")
        assertThat(updated?.type).isEqualTo(ReminderType.RECURRING)
        assertThat(updated?.notifyMethod).isEqualTo(NotificationMethod.BOTH)
        assertThat(updated?.message).isEqualTo("message2")
        assertThat(updated?.spec).isEqualTo("500")
        assertThat(reminderService.get(res1.reminderId)).isEqualTo(updated)

        // cannot update entryId
        val updated2 = reminderService.updateReminder(NewReminder(res2.reminderId, "e2", ReminderType.ADHOC, NotificationMethod.BOTH, "message3", "800", "America/New_York"))
        assertThat(updated2?.entryId).isEqualTo("e1")
        assertThat(updated2?.type).isEqualTo(ReminderType.ADHOC)
        assertThat(updated2?.notifyMethod).isEqualTo(NotificationMethod.BOTH)
        assertThat(updated2?.message).isEqualTo("message3")
        assertThat(updated2?.spec).isEqualTo("800")
        assertThat(updated2?.tz).isEqualTo("America/New_York")
        assertThat(reminderService.get(res2.reminderId)).isEqualTo(updated2)

        verify(exactly = 4) { workerRegistry.acceptReminderWork(any()) }
    }

    @Test
    fun testUpdateReminderInvalidTimeZone() {
        val res1 = reminderService.addReminder(NewReminder(null, "e1", ReminderType.ADHOC, NotificationMethod.PUSH, "message", "100", tz))
        assertThrows<IllegalArgumentException> {
            reminderService.updateReminder(NewReminder(res1.reminderId, "e1", ReminderType.RECURRING, NotificationMethod.EMAIL, "message", "500", "invalid"))
        }
    }

    @Test
    fun testDeleteReminder() {
        val s1 = reminderService.add(reminder("sid", "e2", ReminderType.RECURRING, NotificationMethod.BOTH, "message", 300))
        assertThat(reminderService.getAllReminders()).hasSize(1)

        assertThat(reminderService.delete(s1.reminderId)).isTrue()
        assertThat(reminderService.get(s1.reminderId)).isNull()
        assertThat(reminderService.getAllReminders()).isEmpty()
        verify(exactly = 2) { workerRegistry.acceptReminderWork(any()) }
    }

    @Test
    fun testIsActive() {
        val res1 = reminderService.addReminder(NewReminder(null, "e1", ReminderType.ADHOC, NotificationMethod.BOTH, "elapsed", "100", tz))
        val res2 = reminderService.addReminder(NewReminder(null, "e1", ReminderType.RECURRING, NotificationMethod.BOTH, "elapsed", "200", tz))
        assertThat(reminderService.isActive(res1.reminderId)).isTrue()
        assertThat(reminderService.isActive(res2.reminderId)).isTrue()
        assertThat(reminderService.isActive("invalid")).isFalse()
        assertThat(reminderService.isActive("")).isFalse()
    }

    private fun reminder(sid: String, eId: String, type: ReminderType, notifyMethod: NotificationMethod = NotificationMethod.EMAIL,
                         message: String? = null, interval: Long = 0): Reminder {
        return when (type) {
            ReminderType.ADHOC -> AdhocReminder(sid, entryId = eId, message = message, notifyMethod = notifyMethod, interval = interval, tz = this.tz)
            ReminderType.RECURRING -> RecurringReminder(sid, entryId = eId, message = message, notifyMethod = notifyMethod, fire = interval.toString(), tz = this.tz)
        }
    }

}