package service

import common.DatabaseTest
import common.EntryType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import schedule.IntervalJob
import schedule.ScheduleService
import schedule.ScheduleType

class ScheduleServiceTest: DatabaseTest() {

    private val scheduleService = ScheduleService()

    @BeforeEach
    fun insertEntry() {
        createDummyEntry("e1", "title", "content", EntryType.NOTE)
        createDummyEntry("e2", "title", "content", EntryType.NOTE)
    }

    @Test
    fun testAddNewIntervalSchedule() {
        scheduleService.add(intervalJob("e1", ScheduleType.DISCUSSION_FINDER, 100))

        val res = scheduleService.getIntervalJobsByType(ScheduleType.DISCUSSION_FINDER)
        assertThat(res).hasSize(1)
        assertThat(res[0].entryId).isEqualTo("e1")
        assertThat(res[0].type).isEqualTo(ScheduleType.DISCUSSION_FINDER)
        assertThat(res[0].interval).isEqualTo(100)
    }

    @Test
    fun testGetIntervalJobsByType() {
        scheduleService.add(intervalJob("e1", ScheduleType.DISCUSSION_FINDER, 100))
        scheduleService.add(intervalJob("e2", ScheduleType.DISCUSSION_FINDER, 200))

        val res = scheduleService.getIntervalJobsByType(ScheduleType.DISCUSSION_FINDER)
        assertThat(res).hasSize(2)
        assertThat(res.map { it.entryId }).containsExactlyInAnyOrder("e1", "e2")
        assertThat(res.map { it.type }).containsOnly(ScheduleType.DISCUSSION_FINDER)
        assertThat(res.map { it.interval }).containsExactlyInAnyOrder(100, 200)
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
    fun testDeleteSchedule() {
        val job = intervalJob("e1", ScheduleType.DISCUSSION_FINDER, 100)
        scheduleService.add(job)
        assertThat(scheduleService.getIntervalJobsByType(ScheduleType.DISCUSSION_FINDER)).hasSize(1)
        assertThat(scheduleService.delete(job.scheduleId)).isTrue()
        assertThat(scheduleService.getIntervalJobsByType(ScheduleType.DISCUSSION_FINDER)).isEmpty()
        assertThat(scheduleService.delete(job.scheduleId)).isFalse()
    }

    @Test
    fun testUpdateScheduleNoRow() {
        assertThat(scheduleService.update(intervalJob("e1", ScheduleType.DISCUSSION_FINDER, 200))).isNull()
    }

    fun intervalJob(eId: String, type: ScheduleType, interval: Long=0) = IntervalJob(entryId=eId, type=type, interval=interval)

}