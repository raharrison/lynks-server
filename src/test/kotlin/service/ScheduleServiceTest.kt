package service

import common.DatabaseTest
import common.EntryType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import schedule.ScheduleService
import schedule.ScheduleType
import schedule.ScheduledJobs
import java.sql.SQLException

class ScheduleServiceTest: DatabaseTest() {

    private val scheduleService = ScheduleService()

    @BeforeEach
    fun insertEntry() {
        createDummyEntry("e1", "title", "content", EntryType.NOTE)
        createDummyEntry("e2", "title", "content", EntryType.NOTE)
    }

    @Test
    fun testAddNewSchedule() {
        scheduleService.add("e1", ScheduleType.DISCUSSION_FINDER, 100)

        val res = scheduleService.get("e1", ScheduleType.DISCUSSION_FINDER)
        assertThat(res).hasSize(1)
        assertThat(res[0][ScheduledJobs.entryId]).isEqualTo("e1")
        assertThat(res[0][ScheduledJobs.type]).isEqualTo(ScheduleType.DISCUSSION_FINDER)
        assertThat(res[0][ScheduledJobs.interval]).isEqualTo(100)
    }

    @Test
    fun testAddDuplicateSchedule() {
        assertThrows<SQLException> {
            scheduleService.add("e1", ScheduleType.DISCUSSION_FINDER, 100)
            scheduleService.add("e1", ScheduleType.DISCUSSION_FINDER, 100)
        }
    }

    @Test
    fun testGetScheduleByType() {
        scheduleService.add("e1", ScheduleType.DISCUSSION_FINDER, 100)
        scheduleService.add("e2", ScheduleType.DISCUSSION_FINDER, 200)

        val res = scheduleService.get(ScheduleType.DISCUSSION_FINDER)
        assertThat(res).hasSize(2)
        assertThat(res.map { it[ScheduledJobs.entryId] }).containsExactlyInAnyOrder("e1", "e2")
        assertThat(res.map { it[ScheduledJobs.type] }).containsOnly(ScheduleType.DISCUSSION_FINDER)
        assertThat(res.map { it[ScheduledJobs.interval] }).containsExactlyInAnyOrder(100, 200)
    }

    @Test
    fun testDeleteSchedule() {
        scheduleService.add("e1", ScheduleType.DISCUSSION_FINDER, 100)
        assertThat(scheduleService.get("e1", ScheduleType.DISCUSSION_FINDER)).hasSize(1)
        assertThat(scheduleService.delete("e1", ScheduleType.DISCUSSION_FINDER)).isTrue()
        assertThat(scheduleService.get("e1", ScheduleType.DISCUSSION_FINDER)).isEmpty()
        assertThat(scheduleService.delete("e1", ScheduleType.DISCUSSION_FINDER)).isFalse()
    }

    @Test
    fun testUpdateSchedule() {
        scheduleService.add("e1", ScheduleType.DISCUSSION_FINDER, 100)
        assertThat(scheduleService.update("e1", ScheduleType.DISCUSSION_FINDER, 200)).isEqualTo(1)

        val res = scheduleService.get("e1", ScheduleType.DISCUSSION_FINDER)
        assertThat(res).hasSize(1)
        assertThat(res[0][ScheduledJobs.entryId]).isEqualTo("e1")
        assertThat(res[0][ScheduledJobs.type]).isEqualTo(ScheduleType.DISCUSSION_FINDER)
        assertThat(res[0][ScheduledJobs.interval]).isEqualTo(200)
    }

    @Test
    fun testUpdateScheduleNoRow() {
        assertThat(scheduleService.update("e1", ScheduleType.DISCUSSION_FINDER, 200)).isEqualTo(0)
    }



}