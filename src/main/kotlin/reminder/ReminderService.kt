package reminder

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import util.RandomUtils
import worker.CrudType
import worker.ReminderWorkerRequest
import worker.WorkerRegistry
import java.time.ZoneId

class ReminderService(private val workerRegistry: WorkerRegistry) {

    private fun toModel(row: ResultRow): Reminder {
        return when (row[Reminders.type]) {
            ReminderType.ADHOC -> AdhocReminder(
                row[Reminders.reminderId], row[Reminders.entryId],
                row[Reminders.message], row[Reminders.spec].toLong(), row[Reminders.tz]
            )
            ReminderType.RECURRING -> RecurringReminder(
                row[Reminders.reminderId], row[Reminders.entryId],
                row[Reminders.message], row[Reminders.spec], row[Reminders.tz]
            )
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

    fun add(job: Reminder): Reminder = transaction {
        Reminders.insert {
            it[reminderId] = job.reminderId
            it[entryId] = job.entryId
            it[type] = job.type
            it[message] = job.message
            it[spec] = job.spec
            it[tz] = checkValidTimeZone(job.tz)
        }
        get(job.reminderId)!!.also {
            workerRegistry.acceptReminderWork(ReminderWorkerRequest(it, CrudType.CREATE))
        }
    }

    fun addReminder(reminder: NewReminder): Reminder = transaction {
        val id = RandomUtils.generateUid()
        Reminders.insert {
            it[reminderId] = id
            it[entryId] = reminder.entryId
            it[type] = reminder.type
            it[message] = reminder.message
            it[spec] = reminder.spec
            it[tz] = checkValidTimeZone(reminder.tz)
        }
        get(id)!!.also {
            workerRegistry.acceptReminderWork(ReminderWorkerRequest(it, CrudType.CREATE))
        }
    }

    fun updateReminder(reminder: NewReminder): Reminder? = transaction {
        if (reminder.reminderId == null) {
            addReminder(reminder)
        } else {
            val updatedCount = Reminders.update({ Reminders.reminderId eq reminder.reminderId }) {
                it[type] = reminder.type
                it[message] = reminder.message
                it[spec] = reminder.spec
                it[tz] = checkValidTimeZone(reminder.tz)
            }
            if (updatedCount > 0) {
                get(reminder.reminderId)?.also {
                    workerRegistry.acceptReminderWork(ReminderWorkerRequest(it, CrudType.UPDATE))
                }
            } else null
        }
    }

    fun delete(id: String): Boolean = transaction {
        val reminder = get(id)
        if (reminder != null) {
            Reminders.deleteWhere { Reminders.reminderId eq id }
            workerRegistry.acceptReminderWork(ReminderWorkerRequest(reminder, CrudType.DELETE))
            return@transaction true
        }
        false
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