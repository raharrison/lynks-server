package schedule

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import schedule.ScheduledJobs.entryId
import schedule.ScheduledJobs.type

class ScheduleService {

    fun get(scheduleType: ScheduleType) = transaction {
        ScheduledJobs.select { type eq scheduleType }.toList()
    }

    fun get(eId: String, scheduleType: ScheduleType) = transaction {
        ScheduledJobs.select { (entryId eq eId) and (type eq scheduleType) }.toList()
    }

    fun add(eId: String, scheduleType: ScheduleType, interval: Long) {
        ScheduledJobs.insert {
            it[ScheduledJobs.entryId] = eId
            it[ScheduledJobs.type] = scheduleType
            it[ScheduledJobs.interval] = interval
        }
    }

    fun update(linkId: String, scheduleType: ScheduleType, interval: Long) {
        ScheduledJobs.update( { (entryId eq linkId) and
                (type eq scheduleType)}) {
            it[ScheduledJobs.interval] = interval
        }
    }

    fun delete(eId: String, scheduleType: ScheduleType) = transaction {
        ScheduledJobs.deleteWhere { (entryId eq eId) and (type eq scheduleType) }
    }

}