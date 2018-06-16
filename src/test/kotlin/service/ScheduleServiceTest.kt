package service

import common.DatabaseTest
import common.EntryType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import schedule.ScheduleService
import schedule.ScheduleType
import schedule.ScheduledJob
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
        scheduleService.add(job("e1", ScheduleType.DISCUSSION_FINDER, 100))

        val res = scheduleService.get("e1", ScheduleType.DISCUSSION_FINDER)
        assertThat(res).hasSize(1)
        assertThat(res[0].entryId).isEqualTo("e1")
        assertThat(res[0].type).isEqualTo(ScheduleType.DISCUSSION_FINDER)
        assertThat(res[0].interval).isEqualTo(100)
    }

    @Test
    fun testAddDuplicateSchedule() {
        assertThrows<SQLException> {
            scheduleService.add(job("e1", ScheduleType.DISCUSSION_FINDER, 100))
            scheduleService.add(job("e1", ScheduleType.DISCUSSION_FINDER, 100))
        }
    }

    @Test
    fun testGetScheduleByType() {
        scheduleService.add(job("e1", ScheduleType.DISCUSSION_FINDER, 100))
        scheduleService.add(job("e2", ScheduleType.DISCUSSION_FINDER, 200))

        val res = scheduleService.get(ScheduleType.DISCUSSION_FINDER)
        assertThat(res).hasSize(2)
        assertThat(res.map { it.entryId }).containsExactlyInAnyOrder("e1", "e2")
        assertThat(res.map { it.type }).containsOnly(ScheduleType.DISCUSSION_FINDER)
        assertThat(res.map { it.interval }).containsExactlyInAnyOrder(100, 200)
    }

    @Test
    fun testDeleteSchedule() {
        scheduleService.add(job("e1", ScheduleType.DISCUSSION_FINDER, 100))
        assertThat(scheduleService.get("e1", ScheduleType.DISCUSSION_FINDER)).hasSize(1)
        assertThat(scheduleService.delete(job("e1", ScheduleType.DISCUSSION_FINDER))).isTrue()
        assertThat(scheduleService.get("e1", ScheduleType.DISCUSSION_FINDER)).isEmpty()
        assertThat(scheduleService.delete(job("e1", ScheduleType.DISCUSSION_FINDER))).isFalse()
    }

    @Test
    fun testUpdateSchedule() {
        scheduleService.add(job("e1", ScheduleType.DISCUSSION_FINDER, 100))
        assertThat(scheduleService.update(job("e1", ScheduleType.DISCUSSION_FINDER, 200))).isEqualTo(1)

        val res = scheduleService.get("e1", ScheduleType.DISCUSSION_FINDER)
        assertThat(res).hasSize(1)
        assertThat(res[0].entryId).isEqualTo("e1")
        assertThat(res[0].type).isEqualTo(ScheduleType.DISCUSSION_FINDER)
        assertThat(res[0].interval).isEqualTo(200)
    }

    @Test
    fun testUpdateScheduleNoRow() {
        assertThat(scheduleService.update(job("e1", ScheduleType.DISCUSSION_FINDER, 200))).isEqualTo(0)
    }

    fun job(eId: String, type: ScheduleType, interval: Long=0) = ScheduledJob(eId, type, interval)

}