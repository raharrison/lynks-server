package lynks.service

import io.mockk.mockk
import io.mockk.verify
import lynks.common.DatabaseTest
import lynks.common.EntryType
import lynks.common.exception.InvalidModelException
import lynks.notify.NotificationMethod
import lynks.reminder.*
import lynks.util.createDummyEntry
import lynks.worker.WorkerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
        val reminder = reminderService.add(reminder("sid", "e1", ReminderType.ADHOC,
            listOf(NotificationMethod.EMAIL, NotificationMethod.WEB), "text", 100, ReminderStatus.ACTIVE))
        assertThat(reminder.reminderId).isEqualTo("sid")
        assertThat(reminder.entryId).isEqualTo("e1")
        assertThat(reminder.spec).isEqualTo("100")
        assertThat(reminder.message).isEqualTo("text")
        assertThat(reminder.notifyMethods).containsExactly(NotificationMethod.EMAIL, NotificationMethod.WEB)
        assertThat(reminder.dateCreated).isEqualTo(reminder.dateUpdated)
        assertThat(reminder.status).isEqualTo(ReminderStatus.ACTIVE)
        val retrieved = reminderService.get(reminder.reminderId)
        assertThat(retrieved).isEqualTo(reminder)
        verify(exactly = 1) { workerRegistry.acceptReminderWork(any()) }
    }

    @Test
    fun testAddNewReminder() {
        val reminder1 = NewReminder(null, "e1", ReminderType.ADHOC,
            listOf(NotificationMethod.EMAIL, NotificationMethod.WEB), "message", "100", tz, ReminderStatus.ACTIVE)
        val reminder2 = NewReminder(null, "e1", ReminderType.RECURRING,
            listOf(NotificationMethod.PUSHOVER, NotificationMethod.EMAIL), "message2", "every", tz, ReminderStatus.DISABLED)
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
        assertThat(retrieved?.notifyMethods).containsExactly(NotificationMethod.EMAIL, NotificationMethod.WEB)
        assertThat(retrieved2?.notifyMethods).containsExactly(NotificationMethod.PUSHOVER, NotificationMethod.EMAIL)
        assertThat(retrieved?.dateCreated).isEqualTo(retrieved?.dateUpdated)
        assertThat(retrieved2?.dateCreated).isEqualTo(retrieved2?.dateUpdated)
        assertThat(retrieved?.status).isEqualTo(ReminderStatus.ACTIVE)
        assertThat(retrieved2?.status).isEqualTo(ReminderStatus.DISABLED)
        verify(exactly = 2) { workerRegistry.acceptReminderWork(any()) }
    }

    @Test
    fun testAddReminderInvalidTimeZone() {
        assertThrows<IllegalArgumentException> {
            reminderService.addReminder(NewReminder(null, "e1", ReminderType.ADHOC,
                listOf(NotificationMethod.WEB), "message", "100", "invalid", ReminderStatus.ACTIVE))
        }
    }

    @Test
    fun testAddReminderGivenIdDoesNotPerformUpdate() {
        val rem = reminderService.addReminder(NewReminder(null, "e1", ReminderType.ADHOC,
            listOf(NotificationMethod.EMAIL), "message", "100", tz, ReminderStatus.ACTIVE))
        val res = reminderService.addReminder(NewReminder(rem.reminderId, "e1", ReminderType.RECURRING,
            listOf(NotificationMethod.WEB), "message", "200", tz, ReminderStatus.COMPLETED))
        assertThat(rem.reminderId).isNotEqualTo(res.reminderId)
        assertThat(res.entryId).isEqualTo("e1")
        assertThat(res.type).isEqualTo(ReminderType.RECURRING)
        assertThat(res.message).isEqualTo("message")
        assertThat(res.spec).isEqualTo("200")
        assertThat(res.tz).isEqualTo(tz)
        assertThat(rem.dateCreated).isEqualTo(rem.dateUpdated)
        assertThat(res.dateCreated).isEqualTo(res.dateUpdated)
        assertThat(res.notifyMethods).containsExactly(NotificationMethod.WEB)
        assertThat(rem.status).isEqualTo(ReminderStatus.ACTIVE)
        assertThat(res.status).isEqualTo(ReminderStatus.COMPLETED)
        assertThat(reminderService.getAllReminders()).hasSize(2)
        assertThat(reminderService.get(res.reminderId)).isEqualTo(res)
        assertThat(reminderService.get(rem.reminderId)).isEqualTo(rem)
        verify(exactly = 2) { workerRegistry.acceptReminderWork(any()) }
    }

    @Test
    fun testAddReminderNoEntry() {
        assertThrows<SQLException> {
            reminderService.add(reminder("sid", "nothing", ReminderType.ADHOC,
                listOf(NotificationMethod.WEB), "message", 100, ReminderStatus.ACTIVE))
        }
        assertThrows<SQLException> {
            reminderService.addReminder(NewReminder(null, "nothing", ReminderType.ADHOC,
                listOf(NotificationMethod.EMAIL), "message", "100", tz, ReminderStatus.ACTIVE))
        }
    }

    @Test
    fun testGetAllReminders() {
        reminderService.add(reminder("sid", "e1", ReminderType.ADHOC,
            listOf(NotificationMethod.WEB), "message", 100, ReminderStatus.ACTIVE))
        reminderService.add(reminder("sid2", "e1", ReminderType.RECURRING,
            listOf(NotificationMethod.EMAIL), "message", 200, ReminderStatus.COMPLETED))
        reminderService.add(reminder("sid3", "e2", ReminderType.RECURRING,
            listOf(NotificationMethod.EMAIL, NotificationMethod.WEB), "message", 300, ReminderStatus.DISABLED))

        val reminders = reminderService.getAllReminders()
        assertThat(reminders).hasSize(3)
        assertThat(reminders).extracting("reminderId").containsExactlyInAnyOrder("sid", "sid2", "sid3")
        assertThat(reminders).extracting("entryId").containsOnly("e1", "e2")
        assertThat(reminders).extracting("type").containsOnly(ReminderType.RECURRING, ReminderType.ADHOC)
        assertThat(reminders).extracting("notifyMethods").containsExactly(
            listOf(NotificationMethod.WEB), listOf(NotificationMethod.EMAIL),
            listOf(NotificationMethod.EMAIL, NotificationMethod.WEB)
        )
        assertThat(reminders).extracting("message").containsOnly("message")
        assertThat(reminders).extracting("spec").doesNotContain("400")
        assertThat(reminders).extracting("tz").containsOnly(tz)
        assertThat(reminders).extracting("status").containsExactly(ReminderStatus.ACTIVE,
            ReminderStatus.COMPLETED, ReminderStatus.DISABLED)
    }

    @Test
    fun testGetAllActiveReminders() {
        reminderService.add(reminder("sid", "e1", ReminderType.ADHOC,
            listOf(NotificationMethod.WEB), "message", 100, ReminderStatus.COMPLETED))
        reminderService.add(reminder("sid2", "e1", ReminderType.RECURRING,
            listOf(NotificationMethod.EMAIL), "message", 200, ReminderStatus.ACTIVE))
        reminderService.add(reminder("sid3", "e2", ReminderType.RECURRING,
            listOf(NotificationMethod.EMAIL, NotificationMethod.WEB), "message", 300, ReminderStatus.DISABLED))

        val reminders = reminderService.getAllActiveReminders()
        assertThat(reminders).hasSize(1)
        assertThat(reminders).extracting("reminderId").containsExactly("sid2")
        assertThat(reminders).extracting("status").containsExactly(ReminderStatus.ACTIVE)
    }

    @Test
    fun testGetRemindersForEntry() {
        reminderService.add(reminder("sid", "e1", ReminderType.ADHOC,
            listOf(NotificationMethod.EMAIL, NotificationMethod.WEB), "message", 100, ReminderStatus.ACTIVE))
        reminderService.add(reminder("sid2", "e1", ReminderType.RECURRING,
            listOf(NotificationMethod.EMAIL), "message", 200, ReminderStatus.COMPLETED))
        reminderService.add(reminder("sid3", "e2", ReminderType.RECURRING,
            listOf(NotificationMethod.WEB), "message", 300, ReminderStatus.DISABLED))

        val remsE1 = reminderService.getRemindersForEntry("e1")
        assertThat(remsE1).hasSize(2).extracting("reminderId").containsExactlyInAnyOrder("sid", "sid2")
        val remsE12 = reminderService.getRemindersForEntry("e2")
        assertThat(remsE12).hasSize(1).extracting("reminderId").containsExactlyInAnyOrder("sid3")
        assertThat(reminderService.getRemindersForEntry("nothing")).isEmpty()
    }

    @Test
    fun testGetRemindersReturnsType() {
        val s1 = reminderService.add(reminder("sid", "e1", ReminderType.ADHOC,
            listOf(NotificationMethod.WEB), "message", 100, ReminderStatus.DISABLED))
        val s2 = reminderService.add(reminder("sid3", "e2", ReminderType.RECURRING,
            listOf(NotificationMethod.EMAIL), "message", 300, ReminderStatus.ACTIVE))
        assertThat(reminderService.get(s1.reminderId)).isInstanceOf(Reminder::class.java)
        assertThat(reminderService.get(s2.reminderId)).isInstanceOf(RecurringReminder::class.java)
    }

    @Test
    fun testUpdateReminderNoRow() {
        assertThat(reminderService.updateReminder(NewReminder("invalid", "e1", ReminderType.ADHOC,
            listOf(NotificationMethod.WEB), "message", "300", tz, ReminderStatus.ACTIVE))).isNull()
        verify(exactly = 0) { workerRegistry.acceptReminderWork(any()) }
    }

    @Test
    fun testUpdateReminderNoId() {
        val res = reminderService.updateReminder(NewReminder(null, "e1", ReminderType.ADHOC,
            listOf(NotificationMethod.EMAIL, NotificationMethod.WEB), "message", "300", tz, ReminderStatus.ACTIVE))
        assertThat(res?.reminderId).isNotBlank()
        assertThat(res?.entryId).isEqualTo("e1")
        assertThat(res?.type).isEqualTo(ReminderType.ADHOC)
        assertThat(res?.notifyMethods).contains(NotificationMethod.EMAIL, NotificationMethod.WEB)
        assertThat(res?.message).isEqualTo("message")
        assertThat(res?.spec).isEqualTo("300")
        assertThat(res?.dateCreated).isEqualTo(res?.dateUpdated)
        assertThat(res?.status).isEqualTo(ReminderStatus.ACTIVE)
        assertThat(reminderService.get(res!!.reminderId)).isEqualTo(res)
        assertThat(reminderService.getAllReminders()).hasSize(1)
        verify(exactly = 1) { workerRegistry.acceptReminderWork(any()) }
    }

    @Test
    fun testUpdateReminder() {
        val res1 = reminderService.addReminder(NewReminder(null, "e1", ReminderType.ADHOC,
            listOf(NotificationMethod.EMAIL), "message", "100", tz, ReminderStatus.ACTIVE))
        val res2 = reminderService.addReminder(NewReminder(null, "e1", ReminderType.RECURRING,
            listOf(NotificationMethod.WEB), "message", "200", tz, ReminderStatus.DISABLED))
        assertThat(reminderService.getAllReminders()).hasSize(2).extracting("reminderId").doesNotHaveDuplicates()

        val updated = reminderService.updateReminder(NewReminder(res1.reminderId, "e1", ReminderType.RECURRING,
            listOf(NotificationMethod.EMAIL, NotificationMethod.PUSHOVER), "message2", "500", tz, ReminderStatus.COMPLETED))
        assertThat(updated?.entryId).isEqualTo("e1")
        assertThat(updated?.type).isEqualTo(ReminderType.RECURRING)
        assertThat(updated?.notifyMethods).containsExactly(NotificationMethod.EMAIL, NotificationMethod.PUSHOVER)
        assertThat(updated?.message).isEqualTo("message2")
        assertThat(updated?.spec).isEqualTo("500")
        assertThat(updated?.dateCreated).isLessThanOrEqualTo(updated?.dateUpdated)
        assertThat(updated?.status).isEqualTo(ReminderStatus.COMPLETED)
        assertThat(reminderService.get(res1.reminderId)).isEqualTo(updated)

        // cannot update entryId
        val updated2 = reminderService.updateReminder(NewReminder(res2.reminderId, "e2", ReminderType.ADHOC,
            listOf(NotificationMethod.EMAIL), "message3", "800", "America/New_York", ReminderStatus.ACTIVE))
        assertThat(updated2?.entryId).isEqualTo("e1")
        assertThat(updated2?.type).isEqualTo(ReminderType.ADHOC)
        assertThat(updated2?.notifyMethods).containsOnly(NotificationMethod.EMAIL)
        assertThat(updated2?.message).isEqualTo("message3")
        assertThat(updated2?.spec).isEqualTo("800")
        assertThat(updated2?.tz).isEqualTo("America/New_York")
        assertThat(updated2?.dateCreated).isNotEqualTo(updated2?.dateUpdated)
        assertThat(updated2?.status).isEqualTo(ReminderStatus.ACTIVE)
        assertThat(reminderService.get(res2.reminderId)).isEqualTo(updated2)

        verify(exactly = 4) { workerRegistry.acceptReminderWork(any()) }
    }

    @Test
    fun testUpdateReminderInvalidTimeZone() {
        val res1 = reminderService.addReminder(NewReminder(null, "e1", ReminderType.ADHOC,
            listOf(NotificationMethod.PUSHOVER), "message", "100", tz, ReminderStatus.ACTIVE))
        assertThrows<IllegalArgumentException> {
            reminderService.updateReminder(NewReminder(res1.reminderId, "e1", ReminderType.RECURRING,
                listOf(NotificationMethod.WEB), "message", "500", "invalid", ReminderStatus.DISABLED))
        }
    }

    @Test
    fun testUpdateReminderStatus() {
        val res1 = reminderService.addReminder(NewReminder(null, "e1", ReminderType.ADHOC,
            listOf(NotificationMethod.PUSHOVER), "message", "100", tz, ReminderStatus.ACTIVE))
        val updated = reminderService.updateReminderStatus(res1.reminderId, ReminderStatus.COMPLETED)
        assertThat(updated).isOne()
        val retrieved = reminderService.get(res1.reminderId)
        assertThat(retrieved?.status).isEqualTo(ReminderStatus.COMPLETED)
    }

    @Test
    fun testDeleteReminder() {
        val s1 = reminderService.add(reminder("sid", "e2", ReminderType.RECURRING,
            listOf(NotificationMethod.EMAIL), "message", 300, ReminderStatus.ACTIVE))
        assertThat(reminderService.getAllReminders()).hasSize(1)

        assertThat(reminderService.delete(s1.reminderId)).isTrue()
        assertThat(reminderService.get(s1.reminderId)).isNull()
        assertThat(reminderService.getAllReminders()).isEmpty()
        verify(exactly = 2) { workerRegistry.acceptReminderWork(any()) }
    }

    @Test
    fun testIsActive() {
        val res1 = reminderService.addReminder(NewReminder(null, "e1", ReminderType.ADHOC,
            listOf(NotificationMethod.EMAIL), "elapsed", "100", tz, ReminderStatus.ACTIVE))
        val res2 = reminderService.addReminder(NewReminder(null, "e1", ReminderType.RECURRING,
            listOf(NotificationMethod.WEB), "elapsed", "200", tz, ReminderStatus.DISABLED))
        assertThat(reminderService.isActive(res1.reminderId)).isTrue()
        assertThat(reminderService.isActive(res2.reminderId)).isFalse()
        assertThat(reminderService.isActive("invalid")).isFalse()
        assertThat(reminderService.isActive("")).isFalse()
    }

    @Test
    fun testValidateSchedule() {
        val nextFireTimes = reminderService.validateAndTranscribeSchedule("every day 17:00")
        assertThat(nextFireTimes).hasSize(5)
        assertThrows<InvalidModelException> { reminderService.validateAndTranscribeSchedule("every invalid 17:00") }
    }

    private fun reminder(sid: String, eId: String, type: ReminderType, notifyMethods: List<NotificationMethod> = listOf(NotificationMethod.EMAIL),
                         message: String? = null, interval: Long = 0, status: ReminderStatus): Reminder {
        val time = System.currentTimeMillis()
        return when (type) {
            ReminderType.ADHOC -> AdhocReminder(sid, entryId = eId, message = message, notifyMethods = notifyMethods,
                interval = interval, tz = this.tz, status = status, dateCreated = time, dateUpdated = time)
            ReminderType.RECURRING -> RecurringReminder(sid, entryId = eId, message = message, notifyMethods = notifyMethods,
                fire = interval.toString(), tz = this.tz, status = status, dateCreated = time, dateUpdated = time)
        }
    }

}
