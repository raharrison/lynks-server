package schedule

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import util.RandomUtils
import java.time.ZoneId

class ScheduleService {

    private fun toModel(row: ResultRow): Schedule {
        val type = row[Schedules.type]
        return when (type) {
            ScheduleType.REMINDER -> Reminder(row[Schedules.scheduleId], row[Schedules.entryId],
                    type, row[Schedules.spec].toLong(), row[Schedules.tz])
            ScheduleType.RECURRING -> RecurringReminder(row[Schedules.scheduleId], row[Schedules.entryId],
                    type, row[Schedules.spec], row[Schedules.tz])
            else -> IntervalJob(row[Schedules.scheduleId], row[Schedules.entryId],
                    type, row[Schedules.spec].toLong(), row[Schedules.tz])
        }
    }

    fun getIntervalJobsByType(type: ScheduleType) = transaction {
        Schedules.select {
            (Schedules.type eq type) and
                    (Schedules.type neq ScheduleType.REMINDER) and
                    (Schedules.type neq ScheduleType.RECURRING)
        }.map {
            IntervalJob(it[Schedules.scheduleId], it[Schedules.entryId],
                    it[Schedules.type], it[Schedules.spec].toLong(), it[Schedules.tz])
        }
    }

    fun getRemindersForEntry(eId: String) = transaction {
        Schedules.select {
            (Schedules.entryId eq eId) and ((Schedules.type eq ScheduleType.RECURRING) or
                    (Schedules.type eq ScheduleType.REMINDER))
        }
                .map { toModel(it) }
    }

    fun getAllReminders() = transaction {
        Schedules.select { (Schedules.type eq ScheduleType.RECURRING) or (Schedules.type eq ScheduleType.REMINDER) }
                .map { toModel(it) }
    }

    fun get(id: String): Schedule? = transaction {
        Schedules.select { Schedules.scheduleId eq id }
                .mapNotNull { toModel(it) }.singleOrNull()
    }

    fun isActive(id: String): Boolean = transaction {
        Schedules.slice(Schedules.scheduleId)
                .select { Schedules.scheduleId eq id }.count() > 0
    }

    fun add(job: Schedule) = transaction {
        Schedules.insert {
            it[Schedules.scheduleId] = job.scheduleId
            it[Schedules.entryId] = job.entryId
            it[Schedules.type] = job.type
            it[Schedules.spec] = job.spec
            it[Schedules.tz] = checkValidTimeZone(job.tz)
        }
        get(job.scheduleId)!!
    }

    fun addReminder(reminder: NewReminder): Schedule = transaction {
        val id = RandomUtils.generateUid()
        Schedules.insert {
            it[Schedules.scheduleId] = id
            it[Schedules.entryId] = reminder.entryId
            it[Schedules.type] = reminder.type
            it[Schedules.spec] = reminder.spec
            it[Schedules.tz] = checkValidTimeZone(reminder.tz)
        }
        get(id)!!
    }

    fun updateReminder(reminder: NewReminder): Schedule? = transaction {
        if (reminder.scheduleId == null) {
            addReminder(reminder)
        } else {
            val updated = Schedules.update({ Schedules.scheduleId eq reminder.scheduleId }) {
                it[Schedules.type] = reminder.type
                it[Schedules.spec] = reminder.spec
                it[Schedules.tz] = checkValidTimeZone(reminder.tz)
            }
            if(updated > 0) get(reminder.scheduleId) else null
        }
    }

    fun update(job: Schedule): Schedule? = transaction {
        Schedules.update({ Schedules.scheduleId eq job.scheduleId }) {
            it[Schedules.type] = job.type
            it[Schedules.spec] = job.spec
            it[Schedules.tz] = checkValidTimeZone(job.tz)
        }
        get(job.scheduleId)
    }

    fun delete(id: String) = transaction {
        Schedules.deleteWhere { Schedules.scheduleId eq id } > 0
    }

    private fun checkValidTimeZone(tz: String): String {
        try {
            ZoneId.of(tz)
            return tz
        } catch(e: Exception) {
            throw IllegalArgumentException("Invalid timezone code: $tz")
        }
    }

}