package lynks.reminder

import com.github.shyiko.skedule.InvalidScheduleException
import com.github.shyiko.skedule.Schedule
import lynks.common.EntryId
import lynks.common.ReminderId
import lynks.common.exception.InvalidModelException
import lynks.common.newReminderId
import lynks.notify.NotificationMethod
import lynks.util.loggerFor
import lynks.worker.CrudType
import lynks.worker.ReminderWorkerRequest
import lynks.worker.WorkerRegistry
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class ReminderService(private val workerRegistry: WorkerRegistry) {

    private val log = loggerFor<ReminderService>()
    private val scheduleFormatter = DateTimeFormatter.ofPattern("EEE dd MMMM yyyy 'at' HH:mm")

    private fun toModel(row: ResultRow): Reminder {
        return when (row[Reminders.type]) {
            ReminderType.ADHOC -> AdhocReminder(
                ReminderId(row[Reminders.reminderId]), EntryId(row[Reminders.entryId]), toNotifyMethods(row[Reminders.notifyMethods]),
                    row[Reminders.message], row[Reminders.spec].toLong(), row[Reminders.tz], row[Reminders.status],
                    row[Reminders.dateCreated], row[Reminders.dateUpdated]
            )
            ReminderType.RECURRING -> RecurringReminder(
                    ReminderId(row[Reminders.reminderId]), EntryId(row[Reminders.entryId]), toNotifyMethods(row[Reminders.notifyMethods]),
                    row[Reminders.message], row[Reminders.spec], row[Reminders.tz], row[Reminders.status],
                    row[Reminders.dateCreated], row[Reminders.dateUpdated]
            )
        }
    }

    // convert stored comma-separated set of methods to list of enum
    private fun toNotifyMethods(str: String): List<NotificationMethod> {
        return str.split(',').map { NotificationMethod.valueOf(it) }
    }

    fun getRemindersForEntry(eId: EntryId) = transaction {
        Reminders.selectAll().where { Reminders.entryId eq eId.value }
            .orderBy(Reminders.dateUpdated, SortOrder.DESC)
            .map { toModel(it) }
    }

    fun getAllReminders() = transaction {
        Reminders.selectAll()
            .orderBy(Reminders.dateUpdated, SortOrder.DESC)
            .map { toModel(it) }
    }

    fun getAllActiveReminders() = transaction {
        Reminders.selectAll().where { Reminders.status eq ReminderStatus.ACTIVE }
            .map { toModel(it) }
    }

    fun get(id: ReminderId): Reminder? = transaction {
        Reminders.selectAll().where { Reminders.reminderId eq id.value }
                .mapNotNull { toModel(it) }.singleOrNull()
    }

    fun isActive(id: ReminderId): Boolean = transaction {
        Reminders.select(Reminders.reminderId)
            .where {
                (Reminders.reminderId eq id.value) and
                    (Reminders.status eq ReminderStatus.ACTIVE)
            }.count() > 0
    }

    fun add(reminder: Reminder): Reminder = transaction {
        val time = System.currentTimeMillis()
        Reminders.insert {
            it[reminderId] = reminder.reminderId.value
            it[entryId] = reminder.entryId.value
            it[type] = reminder.type
            it[notifyMethods] = reminder.notifyMethods.joinToString(",")
            it[message] = reminder.message
            it[spec] = reminder.spec
            it[tz] = checkValidTimeZone(reminder.tz)
            it[status] = reminder.status
            it[dateCreated] = time
            it[dateUpdated] = time
        }
        val created = get(reminder.reminderId)
            ?: throw IllegalStateException("Reminder ${reminder.reminderId.value} not found after insert")
        log.info("Created reminder, submitting worker request id={}", reminder.reminderId)
        workerRegistry.acceptReminderWork(ReminderWorkerRequest(created, CrudType.CREATE))
        created
    }

    fun addReminder(reminder: NewReminder): Reminder = transaction {
        val id = newReminderId()
        val time = System.currentTimeMillis()
        Reminders.insert {
            it[reminderId] = id.value
            it[entryId] = reminder.entryId.value
            it[type] = reminder.type
            it[notifyMethods] = reminder.notifyMethods.joinToString(",")
            it[message] = reminder.message
            it[spec] = reminder.spec
            it[tz] = checkValidTimeZone(reminder.tz)
            it[status] = reminder.status
            it[dateCreated] = time
            it[dateUpdated] = time
        }
        val created = get(id)
            ?: throw IllegalStateException("Reminder ${id.value} not found after insert")
        log.info("Created reminder, submitting worker request id={}", id.value)
        workerRegistry.acceptReminderWork(ReminderWorkerRequest(created, CrudType.CREATE))
        created
    }

    fun updateReminder(reminder: NewReminder): Reminder? = transaction {
        if (reminder.reminderId == null) {
            log.info("No reminder id found, defaulting to adding new reminder")
            addReminder(reminder)
        } else {
            val updatedCount = Reminders.update({ Reminders.reminderId eq reminder.reminderId.value }) {
                it[type] = reminder.type
                it[notifyMethods] = reminder.notifyMethods.joinToString(",")
                it[message] = reminder.message
                it[spec] = reminder.spec
                it[tz] = checkValidTimeZone(reminder.tz)
                it[status] = reminder.status
                it[dateUpdated] = System.currentTimeMillis()
            }
            if (updatedCount > 0) {
                get(reminder.reminderId)?.also {
                    log.info("Updated reminder, submitting worker request id={}", reminder.reminderId)
                    workerRegistry.acceptReminderWork(ReminderWorkerRequest(it, CrudType.UPDATE))
                }
            } else {
                log.info("No rows modified when updating reminder id={}", reminder.reminderId)
                null
            }
        }
    }

    fun updateReminderStatus(reminderId: ReminderId, status: ReminderStatus) = transaction {
        Reminders.update({ Reminders.reminderId eq reminderId.value }) {
            it[Reminders.status] = status
        }
    }

    fun delete(id: ReminderId): Boolean = transaction {
        val reminder = get(id)
        if (reminder != null) {
            Reminders.deleteWhere { Reminders.reminderId eq id.value }
            workerRegistry.acceptReminderWork(ReminderWorkerRequest(reminder, CrudType.DELETE))
            return@transaction true
        }
        log.info("No reminder found with id={}", id)
        false
    }

    fun validateAndTranscribeSchedule(definition: String): List<String> {
        val schedule = try {
            Schedule.parse(definition)
        } catch (e: InvalidScheduleException) {
            throw InvalidModelException(e.message ?: "Invalid schedule definition")
        }
        val now = ZonedDateTime.now()
        val iterator = schedule.iterate(now)
        return (1..5).map { iterator.next().format(scheduleFormatter) }
    }

    private fun checkValidTimeZone(tz: String): String {
        try {
            ZoneId.of(tz)
            return tz
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid timezone code: $tz")
        }
    }

}
