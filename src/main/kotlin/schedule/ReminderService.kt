package schedule

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import util.RandomUtils
import java.time.ZoneId

class ReminderService {

    private fun toModel(row: ResultRow): Reminder {
        val type = row[Reminders.type]
        return when (type) {
            ReminderType.ADHOC -> AdhocReminder(row[Reminders.reminderId], row[Reminders.entryId],
                    row[Reminders.message], row[Reminders.spec].toLong(), row[Reminders.tz])
            ReminderType.RECURRING -> RecurringReminder(row[Reminders.reminderId], row[Reminders.entryId],
                    row[Reminders.message], row[Reminders.spec], row[Reminders.tz])
        }
    }

    fun getRemindersForEntry(eId: String) = transaction {
        Reminders.select { Reminders.entryId eq eId }
                .map { toModel(it) }
    }

    fun getAllReminders() = transaction {
        Reminders.selectAll()
                .map { toModel(it) }
    }

    fun get(id: String): Reminder? = transaction {
        Reminders.select { Reminders.reminderId eq id }
                .mapNotNull { toModel(it) }.singleOrNull()
    }

    fun isActive(id: String): Boolean = transaction {
        Reminders.slice(Reminders.reminderId)
                .select { Reminders.reminderId eq id }.count() > 0
    }

    fun add(job: Reminder) = transaction {
        Reminders.insert {
            it[Reminders.reminderId] = job.reminderId
            it[Reminders.entryId] = job.entryId
            it[Reminders.type] = job.type
            it[Reminders.message] = job.message
            it[Reminders.spec] = job.spec
            it[Reminders.tz] = checkValidTimeZone(job.tz)
        }
        get(job.reminderId)!!
    }

    fun addReminder(reminder: NewReminder): Reminder = transaction {
        val id = RandomUtils.generateUid()
        Reminders.insert {
            it[Reminders.reminderId] = id
            it[Reminders.entryId] = reminder.entryId
            it[Reminders.type] = reminder.type
            it[Reminders.message] = reminder.message
            it[Reminders.spec] = reminder.spec
            it[Reminders.tz] = checkValidTimeZone(reminder.tz)
        }
        get(id)!!
    }

    fun updateReminder(reminder: NewReminder): Reminder? = transaction {
        if (reminder.reminderId == null) {
            addReminder(reminder)
        } else {
            val updated = Reminders.update({ Reminders.reminderId eq reminder.reminderId }) {
                it[Reminders.type] = reminder.type
                it[Reminders.message] = reminder.message
                it[Reminders.spec] = reminder.spec
                it[Reminders.tz] = checkValidTimeZone(reminder.tz)
            }
            if (updated > 0) get(reminder.reminderId) else null
        }
    }

    fun delete(id: String) = transaction {
        Reminders.deleteWhere { Reminders.reminderId eq id } > 0
    }

    private fun checkValidTimeZone(tz: String): String {
        try {
            ZoneId.of(tz)
            return tz
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid timezone code: $tz")
        }
    }

}