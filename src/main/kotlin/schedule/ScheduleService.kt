package schedule

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import schedule.ScheduledJobs.entryId
import schedule.ScheduledJobs.type

class ScheduleService {

    fun get(scheduleType: ScheduleType) = transaction {
        ScheduledJobs.select { type eq scheduleType }.map {
            ScheduledJob(it[ScheduledJobs.entryId], it[ScheduledJobs.type], it[ScheduledJobs.interval])
        }
    }

    fun get(eId: String, scheduleType: ScheduleType) = transaction {
        ScheduledJobs.select { (entryId eq eId) and (type eq scheduleType) }.map {
            ScheduledJob(it[ScheduledJobs.entryId], it[ScheduledJobs.type], it[ScheduledJobs.interval])
        }
    }

    fun add(job: ScheduledJob): Unit = transaction {
        ScheduledJobs.insert {
            it[ScheduledJobs.entryId] = job.entryId
            it[ScheduledJobs.type] = job.type
            it[ScheduledJobs.interval] = job.interval
        }
    }

    fun update(job: ScheduledJob) = transaction {
        ScheduledJobs.update( { (entryId eq job.entryId) and
                (type eq job.type)}) {
            it[ScheduledJobs.interval] = job.interval
        }
    }

    fun delete(job: ScheduledJob) = transaction {
        ScheduledJobs.deleteWhere { (entryId eq job.entryId) and (type eq job.type) } > 0
    }

}