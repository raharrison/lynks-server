package service

import common.DatabaseTest
import common.EntryType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import schedule.*
import java.sql.SQLException

class ScheduleServiceTest: DatabaseTest() {

    private val scheduleService = ScheduleService()

    @BeforeEach
    fun insertEntry() {
        createDummyEntry("e1", "title", "content", EntryType.NOTE)
        createDummyEntry("e2", "title", "content", EntryType.NOTE)
    }

    @Test
    fun testAddNewIntervalSchedule() {
        val saved = scheduleService.add(intervalJob("e1", ScheduleType.DISCUSSION_FINDER, 100))
        val res = scheduleService.getIntervalJobsByType(ScheduleType.DISCUSSION_FINDER)
        assertThat(saved).isInstanceOf(IntervalJob::class.java)
        assertThat(res).containsOnly(saved as IntervalJob)
        assertThat(res).hasSize(1)
        assertThat(res[0].entryId).isEqualTo("e1")
        assertThat(res[0].type).isEqualTo(ScheduleType.DISCUSSION_FINDER)
        assertThat(res[0].interval).isEqualTo(100)
    }

    @Test
    fun testAddNewReminderDirectly() {
        val schedule = scheduleService.add(reminder("sid", "e1", ScheduleType.REMINDER, 100))
        assertThat(schedule.scheduleId).isEqualTo("sid")
        assertThat(schedule.entryId).isEqualTo("e1")
        assertThat(schedule.spec).isEqualTo("100")
        val retrieved = scheduleService.get(schedule.scheduleId)
        assertThat(retrieved).isEqualTo(schedule)
    }

    @Test
    fun testAddNewReminder() {
        val reminder1 = NewReminder(null,"e1", ScheduleType.REMINDER, "100")
        val reminder2 = NewReminder(null,"e1", ScheduleType.RECURRING, "every")
        val saved1 = scheduleService.addReminder(reminder1)
        val saved2 = scheduleService.addReminder(reminder2)
        val retrieved = scheduleService.get(saved1.scheduleId)
        val retrieved2 = scheduleService.get(saved2.scheduleId)
        assertThat(retrieved).isEqualTo(saved1)
        assertThat(retrieved2).isEqualTo(saved2)
        assertThat(retrieved?.type).isEqualTo(ScheduleType.REMINDER)
        assertThat(retrieved2?.type).isEqualTo(ScheduleType.RECURRING)
        assertThat(retrieved?.spec).isEqualTo(reminder1.spec)
        assertThat(retrieved2?.spec).isEqualTo(reminder2.spec)
    }

    @Test
    fun testAddReminderGivenIdPerformsUpdate() {
        val rem = scheduleService.addReminder(NewReminder(null,"e1", ScheduleType.REMINDER, "100"))
        val res = scheduleService.addReminder(NewReminder(rem.scheduleId,"e1", ScheduleType.RECURRING, "200"))
        assertThat(res.scheduleId).isEqualTo(rem.scheduleId)
        assertThat(res.entryId).isEqualTo("e1")
        assertThat(res.type).isEqualTo(ScheduleType.RECURRING)
        assertThat(res.spec).isEqualTo("200")
        assertThat(scheduleService.getAllReminders()).hasSize(1)
        assertThat(scheduleService.get(rem.scheduleId)).isEqualTo(res)
    }

    @Test
    fun testAddScheduleNoEntry() {
        assertThrows<SQLException> {
            scheduleService.add(reminder("sid", "nothing", ScheduleType.REMINDER, 100))
        }
        assertThrows<SQLException> {
            scheduleService.addReminder(NewReminder(null,"nothing", ScheduleType.REMINDER, "100"))
        }
    }

    @Test
    fun testGetIntervalJobsByType() {
        scheduleService.add(reminder("sid", "e1", ScheduleType.REMINDER, 100))
        scheduleService.add(reminder("sid2", "e1", ScheduleType.RECURRING, 100))
        scheduleService.add(intervalJob("e1", ScheduleType.DISCUSSION_FINDER, 100))
        scheduleService.add(intervalJob("e2", ScheduleType.DISCUSSION_FINDER, 200))

        val res = scheduleService.getIntervalJobsByType(ScheduleType.DISCUSSION_FINDER)
        assertThat(res).hasSize(2)
        assertThat(res.map { it.entryId }).containsExactlyInAnyOrder("e1", "e2")
        assertThat(res.map { it.type }).containsOnly(ScheduleType.DISCUSSION_FINDER)
        assertThat(res.map { it.interval }).containsExactlyInAnyOrder(100, 200)
    }

    @Test
    fun testGetAllReminders() {
        scheduleService.add(reminder("sid", "e1", ScheduleType.REMINDER, 100))
        scheduleService.add(reminder("sid2", "e1", ScheduleType.RECURRING, 200))
        scheduleService.add(reminder("sid3", "e2", ScheduleType.RECURRING, 300))
        scheduleService.add(intervalJob("e1", ScheduleType.DISCUSSION_FINDER, 400))

        val reminders = scheduleService.getAllReminders()
        assertThat(reminders).hasSize(3)
        assertThat(reminders).extracting("scheduleId").containsExactlyInAnyOrder("sid", "sid2", "sid3")
        assertThat(reminders).extracting("entryId").containsOnly("e1", "e2")
        assertThat(reminders).extracting("type").containsOnly(ScheduleType.RECURRING, ScheduleType.REMINDER)
        assertThat(reminders).extracting("spec").doesNotContain("400")
    }

    @Test
    fun testGetRemindersForEntry() {
        scheduleService.add(reminder("sid", "e1", ScheduleType.REMINDER, 100))
        scheduleService.add(reminder("sid2", "e1", ScheduleType.RECURRING, 200))
        scheduleService.add(reminder("sid3", "e2", ScheduleType.RECURRING, 300))

        val remsE1 = scheduleService.getRemindersForEntry("e1")
        assertThat(remsE1).hasSize(2).extracting("scheduleId").containsExactlyInAnyOrder("sid", "sid2")
        val remsE12 = scheduleService.getRemindersForEntry("e2")
        assertThat(remsE12).hasSize(1).extracting("scheduleId").containsExactlyInAnyOrder("sid3")
        assertThat(scheduleService.getRemindersForEntry("nothing")).isEmpty()
    }

    @Test
    fun testGetSchedulesReturnsType() {
        val s1 = scheduleService.add(reminder("sid", "e1", ScheduleType.REMINDER, 100))
        val s2 = scheduleService.add(reminder("sid3", "e2", ScheduleType.RECURRING, 300))
        val s3 = scheduleService.add(intervalJob("e1", ScheduleType.DISCUSSION_FINDER, 400))
        assertThat(scheduleService.get(s1.scheduleId)).isInstanceOf(Reminder::class.java)
        assertThat(scheduleService.get(s2.scheduleId)).isInstanceOf(RecurringReminder::class.java)
        assertThat(scheduleService.get(s3.scheduleId)).isInstanceOf(IntervalJob::class.java)
    }

    @Test
    fun testUpdateIntervalSchedule() {
        val job = intervalJob("e1", ScheduleType.DISCUSSION_FINDER, 100)
        scheduleService.add(job)
        val copied = job.copy(interval = 200)
        assertThat(scheduleService.update(copied)).isEqualTo(copied)
        assertThat(job.interval).isEqualTo(100)
        assertThat(copied.interval).isEqualTo(200)

        val res = scheduleService.getIntervalJobsByType(ScheduleType.DISCUSSION_FINDER)
        assertThat(res).hasSize(1)
        assertThat(res[0].entryId).isEqualTo("e1")
        assertThat(res[0].type).isEqualTo(ScheduleType.DISCUSSION_FINDER)
        assertThat(res[0].interval).isEqualTo(200)
    }

    @Test
    fun testUpdateScheduleNoRow() {
        assertThat(scheduleService.update(intervalJob("e1", ScheduleType.DISCUSSION_FINDER, 200))).isNull()
    }

    @Test
    fun testUpdateReminderNoId() {
        val res = scheduleService.updateReminder(NewReminder(null, "e1", ScheduleType.REMINDER, "300"))
        assertThat(res.scheduleId).isNotBlank()
        assertThat(res.entryId).isEqualTo("e1")
        assertThat(res.type).isEqualTo(ScheduleType.REMINDER)
        assertThat(res.spec).isEqualTo("300")
        assertThat(scheduleService.get(res.scheduleId)).isEqualTo(res)
        assertThat(scheduleService.getAllReminders()).hasSize(1)
    }

    @Test
    fun testUpdateReminder() {
        val res1 = scheduleService.addReminder(NewReminder(null,"e1", ScheduleType.REMINDER, "100"))
        val res2 = scheduleService.addReminder(NewReminder(null,"e1", ScheduleType.RECURRING, "200"))
        assertThat(scheduleService.getAllReminders()).hasSize(2).extracting("scheduleId").doesNotHaveDuplicates()

        val updated = scheduleService.updateReminder(NewReminder(res1.scheduleId,"e1", ScheduleType.RECURRING, "500"))
        assertThat(updated.entryId).isEqualTo("e1")
        assertThat(updated.type).isEqualTo(ScheduleType.RECURRING)
        assertThat(updated.spec).isEqualTo("500")
        assertThat(scheduleService.get(res1.scheduleId)).isEqualTo(updated)

        // cannot update entryId
        val updated2 = scheduleService.updateReminder(NewReminder(res2.scheduleId,"e2", ScheduleType.REMINDER, "800"))
        assertThat(updated2.entryId).isEqualTo("e1")
        assertThat(updated2.type).isEqualTo(ScheduleType.REMINDER)
        assertThat(updated2.spec).isEqualTo("800")
        assertThat(scheduleService.get(res2.scheduleId)).isEqualTo(updated2)
    }

    @Test
    fun testDeleteSchedule() {
        val s1 = scheduleService.add(reminder("sid", "e2", ScheduleType.RECURRING, 300))
        val s2 = scheduleService.add(intervalJob("e1", ScheduleType.DISCUSSION_FINDER, 400))
        assertThat(scheduleService.getIntervalJobsByType(ScheduleType.DISCUSSION_FINDER)).hasSize(1)
        assertThat(scheduleService.getAllReminders()).hasSize(1)

        assertThat(scheduleService.delete(s1.scheduleId)).isTrue()
        assertThat(scheduleService.get(s1.scheduleId)).isNull()
        assertThat(scheduleService.getAllReminders()).isEmpty()

        assertThat(scheduleService.delete(s2.scheduleId)).isTrue()
        assertThat(scheduleService.get(s2.scheduleId)).isNull()
        assertThat(scheduleService.getIntervalJobsByType(ScheduleType.DISCUSSION_FINDER)).isEmpty()

        assertThat(scheduleService.delete(s2.scheduleId)).isFalse()
    }

    private fun intervalJob(eId: String, type: ScheduleType, interval: Long=0) = IntervalJob(entryId=eId, type=type, interval=interval)
    private fun reminder(sid: String, eId: String, type: ScheduleType, interval: Long=0) = Reminder(sid, entryId=eId, type=type, interval=interval)

}