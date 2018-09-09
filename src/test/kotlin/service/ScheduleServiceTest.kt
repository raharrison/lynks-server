package service

import common.DatabaseTest
import common.EntryType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import schedule.*
import util.createDummyEntry
import java.sql.SQLException
import java.time.ZoneId

class ScheduleServiceTest : DatabaseTest() {

    private val scheduleService = ScheduleService()
    private val tz = ZoneId.systemDefault().id

    @BeforeEach
    fun insertEntry() {
        createDummyEntry("e1", "title", "content", EntryType.NOTE)
        Thread.sleep(10)
        createDummyEntry("e2", "title", "content", EntryType.NOTE)
    }

    @Test
    fun testAddNewReminderDirectly() {
        val schedule = scheduleService.add(reminder("sid", "e1", ReminderType.ADHOC, "message", 100))
        assertThat(schedule.reminderId).isEqualTo("sid")
        assertThat(schedule.entryId).isEqualTo("e1")
        assertThat(schedule.spec).isEqualTo("100")
        assertThat(schedule.message).isEqualTo("message")
        val retrieved = scheduleService.get(schedule.reminderId)
        assertThat(retrieved).isEqualTo(schedule)
    }

    @Test
    fun testAddNewReminder() {
        val reminder1 = NewReminder(null, "e1", ReminderType.ADHOC, "message", "100", tz)
        val reminder2 = NewReminder(null, "e1", ReminderType.RECURRING, "message2", "every", tz)
        val saved1 = scheduleService.addReminder(reminder1)
        val saved2 = scheduleService.addReminder(reminder2)
        val retrieved = scheduleService.get(saved1.reminderId)
        val retrieved2 = scheduleService.get(saved2.reminderId)
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
    }

    @Test
    fun testAddReminderInvalidTimeZone() {
        assertThrows<IllegalArgumentException> {
            scheduleService.addReminder(NewReminder(null, "e1", ReminderType.ADHOC, "message", "100", "invalid"))
        }
    }

    @Test
    fun testAddReminderGivenIdDoesNotPerformUpdate() {
        val rem = scheduleService.addReminder(NewReminder(null, "e1", ReminderType.ADHOC, "message", "100", tz))
        val res = scheduleService.addReminder(NewReminder(rem.reminderId, "e1", ReminderType.RECURRING, "message", "200", tz))
        assertThat(rem.reminderId).isNotEqualTo(res.reminderId)
        assertThat(res.entryId).isEqualTo("e1")
        assertThat(res.type).isEqualTo(ReminderType.RECURRING)
        assertThat(res.message).isEqualTo("message")
        assertThat(res.spec).isEqualTo("200")
        assertThat(res.tz).isEqualTo(tz)
        assertThat(scheduleService.getAllReminders()).hasSize(2)
        assertThat(scheduleService.get(res.reminderId)).isEqualTo(res)
        assertThat(scheduleService.get(rem.reminderId)).isEqualTo(rem)
    }

    @Test
    fun testAddScheduleNoEntry() {
        assertThrows<SQLException> {
            scheduleService.add(reminder("sid", "nothing", ReminderType.ADHOC, "message", 100))
        }
        assertThrows<SQLException> {
            scheduleService.addReminder(NewReminder(null, "nothing", ReminderType.ADHOC, "message", "100", tz))
        }
    }

    @Test
    fun testGetAllReminders() {
        scheduleService.add(reminder("sid", "e1", ReminderType.ADHOC, "message", 100))
        scheduleService.add(reminder("sid2", "e1", ReminderType.RECURRING, "message", 200))
        scheduleService.add(reminder("sid3", "e2", ReminderType.RECURRING, "message", 300))

        val reminders = scheduleService.getAllReminders()
        assertThat(reminders).hasSize(3)
        assertThat(reminders).extracting("reminderId").containsExactlyInAnyOrder("sid", "sid2", "sid3")
        assertThat(reminders).extracting("entryId").containsOnly("e1", "e2")
        assertThat(reminders).extracting("type").containsOnly(ReminderType.RECURRING, ReminderType.ADHOC)
        assertThat(reminders).extracting("message").containsOnly("message")
        assertThat(reminders).extracting("spec").doesNotContain("400")
        assertThat(reminders).extracting("tz").containsOnly(tz)
    }

    @Test
    fun testGetRemindersForEntry() {
        scheduleService.add(reminder("sid", "e1", ReminderType.ADHOC, "message", 100))
        scheduleService.add(reminder("sid2", "e1", ReminderType.RECURRING, "message", 200))
        scheduleService.add(reminder("sid3", "e2", ReminderType.RECURRING, "message", 300))

        val remsE1 = scheduleService.getRemindersForEntry("e1")
        assertThat(remsE1).hasSize(2).extracting("reminderId").containsExactlyInAnyOrder("sid", "sid2")
        val remsE12 = scheduleService.getRemindersForEntry("e2")
        assertThat(remsE12).hasSize(1).extracting("reminderId").containsExactlyInAnyOrder("sid3")
        assertThat(scheduleService.getRemindersForEntry("nothing")).isEmpty()
    }

    @Test
    fun testGetSchedulesReturnsType() {
        val s1 = scheduleService.add(reminder("sid", "e1", ReminderType.ADHOC, "message", 100))
        val s2 = scheduleService.add(reminder("sid3", "e2", ReminderType.RECURRING, "message", 300))
        assertThat(scheduleService.get(s1.reminderId)).isInstanceOf(Reminder::class.java)
        assertThat(scheduleService.get(s2.reminderId)).isInstanceOf(RecurringReminder::class.java)
    }

    @Test
    fun testUpdateReminderNoRow() {
        assertThat(scheduleService.updateReminder(NewReminder("invalid", "e1", ReminderType.ADHOC, "message", "300", tz))).isNull()
    }

    @Test
    fun testUpdateReminderNoId() {
        val res = scheduleService.updateReminder(NewReminder(null, "e1", ReminderType.ADHOC, "message", "300", tz))
        assertThat(res?.reminderId).isNotBlank()
        assertThat(res?.entryId).isEqualTo("e1")
        assertThat(res?.type).isEqualTo(ReminderType.ADHOC)
        assertThat(res?.message).isEqualTo("message")
        assertThat(res?.spec).isEqualTo("300")
        assertThat(scheduleService.get(res!!.reminderId)).isEqualTo(res)
        assertThat(scheduleService.getAllReminders()).hasSize(1)
    }

    @Test
    fun testUpdateReminder() {
        val res1 = scheduleService.addReminder(NewReminder(null, "e1", ReminderType.ADHOC, "message", "100", tz))
        val res2 = scheduleService.addReminder(NewReminder(null, "e1", ReminderType.RECURRING, "message", "200", tz))
        assertThat(scheduleService.getAllReminders()).hasSize(2).extracting("reminderId").doesNotHaveDuplicates()

        val updated = scheduleService.updateReminder(NewReminder(res1.reminderId, "e1", ReminderType.RECURRING, "message2", "500", tz))
        assertThat(updated?.entryId).isEqualTo("e1")
        assertThat(updated?.type).isEqualTo(ReminderType.RECURRING)
        assertThat(updated?.message).isEqualTo("message2")
        assertThat(updated?.spec).isEqualTo("500")
        assertThat(scheduleService.get(res1.reminderId)).isEqualTo(updated)

        // cannot update entryId
        val updated2 = scheduleService.updateReminder(NewReminder(res2.reminderId, "e2", ReminderType.ADHOC, "message3", "800", "America/New_York"))
        assertThat(updated2?.entryId).isEqualTo("e1")
        assertThat(updated2?.type).isEqualTo(ReminderType.ADHOC)
        assertThat(updated2?.message).isEqualTo("message3")
        assertThat(updated2?.spec).isEqualTo("800")
        assertThat(updated2?.tz).isEqualTo("America/New_York")
        assertThat(scheduleService.get(res2.reminderId)).isEqualTo(updated2)
    }

    @Test
    fun testUpdateReminderInvalidTimeZone() {
        val res1 = scheduleService.addReminder(NewReminder(null, "e1", ReminderType.ADHOC, "message", "100", tz))
        assertThrows<IllegalArgumentException> {
            scheduleService.updateReminder(NewReminder(res1.reminderId, "e1", ReminderType.RECURRING, "message", "500", "invalid"))
        }
    }

    @Test
    fun testDeleteSchedule() {
        val s1 = scheduleService.add(reminder("sid", "e2", ReminderType.RECURRING, "message", 300))
        assertThat(scheduleService.getAllReminders()).hasSize(1)

        assertThat(scheduleService.delete(s1.reminderId)).isTrue()
        assertThat(scheduleService.get(s1.reminderId)).isNull()
        assertThat(scheduleService.getAllReminders()).isEmpty()
    }

    @Test
    fun testIsActive() {
        val res1 = scheduleService.addReminder(NewReminder(null, "e1", ReminderType.ADHOC, "message", "100", tz))
        val res2 = scheduleService.addReminder(NewReminder(null, "e1", ReminderType.RECURRING, "message", "200", tz))
        assertThat(scheduleService.isActive(res1.reminderId)).isTrue()
        assertThat(scheduleService.isActive(res2.reminderId)).isTrue()
        assertThat(scheduleService.isActive("invalid")).isFalse()
        assertThat(scheduleService.isActive("")).isFalse()
    }

    private fun reminder(sid: String, eId: String, type: ReminderType, message: String? = null, interval: Long = 0): Reminder {
        return when (type) {
            ReminderType.ADHOC -> AdhocReminder(sid, entryId = eId, message = message, interval = interval, tz = this.tz)
            ReminderType.RECURRING -> RecurringReminder(sid, entryId = eId, message = message, fire = interval.toString(), tz = this.tz)
        }
    }

}