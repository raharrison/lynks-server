package schedule

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import util.RandomUtils

class ScheduleService {

    private fun toModel(row: ResultRow): Schedule {
        val type = row[Schedules.type]
        return when (type) {
            ScheduleType.REMINDER -> Reminder(row[Schedules.scheduleId], row[Schedules.entryId],
                    type, row[Schedules.spec].toLong())
            ScheduleType.RECURRING -> RecurringReminder(row[Schedules.scheduleId], row[Schedules.entryId],
                    type, row[Schedules.spec])
            else -> IntervalJob(row[Schedules.scheduleId], row[Schedules.entryId],
                    type, row[Schedules.spec].toLong())
        }
    }

    fun getIntervalJobsByType(type: ScheduleType) = transaction {
        Schedules.select {
            (Schedules.type eq type) and
                    (Schedules.type neq ScheduleType.REMINDER) and
                    (Schedules.type neq ScheduleType.REMINDER)
        }.map {
            IntervalJob(it[Schedules.scheduleId], it[Schedules.entryId],
                    it[Schedules.type], it[Schedules.spec].toLong())
        }
    }

    fun getRemindersForEntry(eId: String) = transaction {
        Schedules.select {
            (Schedules.scheduleId eq eId) and ((Schedules.type eq ScheduleType.RECURRING) or
                    (Schedules.type eq ScheduleType.REMINDER))
        }
                .map { toModel(it) }
    }

    fun getAllReminders() = transaction {
        Schedules.select { (Schedules.type eq ScheduleType.RECURRING) or (Schedules.type eq ScheduleType.REMINDER) }
                .map { toModel(it) }
    }

    fun get(id: String): Schedule? {
        return Schedules.select { Schedules.scheduleId eq id }
                .mapNotNull { toModel(it) }.singleOrNull()
    }

    fun add(job: Schedule) = transaction {
        Schedules.insert {
            it[Schedules.scheduleId] = job.scheduleId
            it[Schedules.entryId] = job.entryId
            it[Schedules.type] = job.type
            it[Schedules.spec] = job.spec
        }
        get(job.scheduleId)!!
    }

    fun addReminder(reminder: NewReminder): Schedule = transaction {
        if (reminder.scheduleId == null) {
            val id = RandomUtils.generateUid()
            Schedules.insert {
                it[Schedules.scheduleId] = id
                it[Schedules.entryId] = reminder.entryId
                it[Schedules.type] = reminder.type
                it[Schedules.spec] = reminder.spec
            }
            get(id)!!
        } else {
            updateReminder(reminder)
        }
    }

    fun updateReminder(reminder: NewReminder): Schedule = transaction {
        if (reminder.scheduleId == null) {
            addReminder(reminder)
        } else {
            Schedules.update({ Schedules.scheduleId eq reminder.scheduleId }) {
                it[Schedules.type] = reminder.type
                it[Schedules.spec] = reminder.spec
            }
            get(reminder.scheduleId)!!
        }
    }

    fun update(job: Schedule): Schedule? = transaction {
        Schedules.update({ Schedules.scheduleId eq job.scheduleId }) {
            it[Schedules.type] = job.type
            it[Schedules.spec] = job.spec
        }
        get(job.scheduleId)
    }

    fun delete(id: String) = transaction {
        Schedules.deleteWhere { Schedules.scheduleId eq id } > 0
    }

}