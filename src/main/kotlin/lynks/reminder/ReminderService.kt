package lynks.reminder

import lynks.util.RandomUtils
import lynks.util.loggerFor
import lynks.worker.CrudType
import lynks.worker.ReminderWorkerRequest
import lynks.worker.WorkerRegistry
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.ZoneId

class ReminderService(private val workerRegistry: WorkerRegistry) {

    private val log = loggerFor<ReminderService>()

    private fun toModel(row: ResultRow): Reminder {
        return when (row[Reminders.type]) {
            ReminderType.ADHOC -> AdhocReminder(
                    row[Reminders.reminderId], row[Reminders.entryId], row[Reminders.notifyMethod],
                    row[Reminders.message], row[Reminders.spec].toLong(), row[Reminders.tz],
                    row[Reminders.dateCreated], row[Reminders.dateUpdated]
            )
            ReminderType.RECURRING -> RecurringReminder(
                    row[Reminders.reminderId], row[Reminders.entryId], row[Reminders.notifyMethod],
                    row[Reminders.message], row[Reminders.spec], row[Reminders.tz],
                    row[Reminders.dateCreated], row[Reminders.dateUpdated]
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
        val time = System.currentTimeMillis()
        Reminders.insert {
            it[reminderId] = job.reminderId
            it[entryId] = job.entryId
            it[type] = job.type
            it[notifyMethod] = job.notifyMethod
            it[message] = job.message
            it[spec] = job.spec
            it[tz] = checkValidTimeZone(job.tz)
            it[dateCreated] = time
            it[dateUpdated] = time
        }
        get(job.reminderId)!!.also {
            log.info("Created reminder, submitting worker request id={}", job.reminderId)
            workerRegistry.acceptReminderWork(ReminderWorkerRequest(it, CrudType.CREATE))
        }
    }

    fun addReminder(reminder: NewReminder): Reminder = transaction {
        val id = RandomUtils.generateUid()
        val time = System.currentTimeMillis()
        Reminders.insert {
            it[reminderId] = id
            it[entryId] = reminder.entryId
            it[type] = reminder.type
            it[notifyMethod] = reminder.notifyMethod
            it[message] = reminder.message
            it[spec] = reminder.spec
            it[tz] = checkValidTimeZone(reminder.tz)
            it[dateCreated] = time
            it[dateUpdated] = time
        }
        get(id)!!.also {
            log.info("Created reminder, submitting worker request id={}", id)
            workerRegistry.acceptReminderWork(ReminderWorkerRequest(it, CrudType.CREATE))
        }
    }

    fun updateReminder(reminder: NewReminder): Reminder? = transaction {
        if (reminder.reminderId == null) {
            log.info("No reminder id found, defaulting to adding new reminder")
            addReminder(reminder)
        } else {
            val updatedCount = Reminders.update({ Reminders.reminderId eq reminder.reminderId }) {
                it[type] = reminder.type
                it[notifyMethod] = reminder.notifyMethod
                it[message] = reminder.message
                it[spec] = reminder.spec
                it[tz] = checkValidTimeZone(reminder.tz)
                it[dateUpdated] = System.currentTimeMillis()
            }
            if (updatedCount > 0) {
                get(reminder.reminderId)?.also {
                    log.info("Updated reminder, submitting worker request id={}", id)
                    workerRegistry.acceptReminderWork(ReminderWorkerRequest(it, CrudType.UPDATE))
                }
            } else {
                log.info("No rows modified when updating reminder id={}", reminder.reminderId)
                null
            }
        }
    }

    fun delete(id: String): Boolean = transaction {
        val reminder = get(id)
        if (reminder != null) {
            Reminders.deleteWhere { Reminders.reminderId eq id }
            workerRegistry.acceptReminderWork(ReminderWorkerRequest(reminder, CrudType.DELETE))
            return@transaction true
        }
        log.info("No reminder found with id={}", id)
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
