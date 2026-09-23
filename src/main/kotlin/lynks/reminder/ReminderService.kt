package lynks.reminder

import com.github.shyiko.skedule.InvalidScheduleException
import com.github.shyiko.skedule.Schedule
import lynks.common.*
import lynks.common.exception.InvalidModelException
import lynks.common.page.DefaultPageRequest
import lynks.common.page.Page
import lynks.common.page.PageRequest
import lynks.db.EntryOwnership
import lynks.notify.NotificationMethod
import lynks.util.combine
import lynks.util.loggerFor
import lynks.worker.CrudType
import lynks.worker.ReminderWorkerRequest
import lynks.worker.WorkerRegistry
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

class ReminderService(private val workerRegistry: WorkerRegistry) {

    private val log = loggerFor<ReminderService>()
    private val scheduleFormatter = DateTimeFormatter.ofPattern("EEE dd MMMM yyyy 'at' HH:mm")

    private fun toModel(row: ResultRow): Reminder {
        return when (row[Reminders.type]) {
            ReminderType.ADHOC -> AdhocReminder(
                ReminderId(row[Reminders.reminderId]), EntryId(row[Reminders.entryId]), toNotifyMethods(row[Reminders.notifyMethods]),
                    row[Reminders.message], row[Reminders.spec].toLong(), row[Reminders.tz], row[Reminders.status],
                    row[Reminders.dateCreated].toInstant(), row[Reminders.dateUpdated].toInstant()
            )
            ReminderType.RECURRING -> RecurringReminder(
                    ReminderId(row[Reminders.reminderId]), EntryId(row[Reminders.entryId]), toNotifyMethods(row[Reminders.notifyMethods]),
                    row[Reminders.message], row[Reminders.spec], row[Reminders.tz], row[Reminders.status],
                    row[Reminders.dateCreated].toInstant(), row[Reminders.dateUpdated].toInstant()
            )
        }
    }

    // convert stored comma-separated set of methods to list of enum
    private fun toNotifyMethods(str: String): List<NotificationMethod> {
        return str.split(',').map { NotificationMethod.valueOf(it) }
    }

    private fun owned(userId: UserId): Op<Boolean> = Entries.userId eq userId.value

    private fun ownedQuery(userId: UserId) = Reminders.innerJoin(Entries).select(Reminders.columns).where { owned(userId) }

    fun getRemindersForEntry(userId: UserId, eId: EntryId) = transaction {
        ownedQuery(userId).combine { Reminders.entryId eq eId.value }
            .orderBy(Reminders.dateUpdated, SortOrder.DESC)
            .map { toModel(it) }
    }

    fun getAllReminders(userId: UserId, pageRequest: PageRequest = DefaultPageRequest): Page<Reminder> = transaction {
        val baseQuery = ownedQuery(userId)
        Page.of(
            baseQuery.copy()
                .orderBy(Reminders.dateUpdated, SortOrder.DESC)
                .limit(pageRequest.size)
                .offset(max(0, (pageRequest.page - 1) * pageRequest.size))
                .map { toModel(it) },
            pageRequest,
            baseQuery.count()
        )
    }

    // for the worker, which schedules every user's reminders
    fun getAllActiveReminders(): List<Pair<UserId, Reminder>> = transaction {
        Reminders.innerJoin(Entries).select(Reminders.columns + Entries.userId)
            .where { Reminders.status eq ReminderStatus.ACTIVE }
            .map { UserId(it[Entries.userId]) to toModel(it) }
    }

    fun get(userId: UserId, id: ReminderId): Reminder? = transaction {
        ownedQuery(userId).combine { Reminders.reminderId eq id.value }
            .mapNotNull { toModel(it) }.singleOrNull()
    }

    fun isActive(id: ReminderId): Boolean = transaction {
        Reminders.select(Reminders.reminderId)
            .where {
                (Reminders.reminderId eq id.value) and
                    (Reminders.status eq ReminderStatus.ACTIVE)
            }.count() > 0
    }

    fun addReminder(userId: UserId, reminder: NewReminder): Reminder = transaction {
        if (!EntryOwnership.isOwner(userId, reminder.entryId)) {
            throw InvalidModelException("Unknown entry: ${reminder.entryId}")
        }
        val id = newReminderId()
        val time = OffsetDateTime.now(ZoneOffset.UTC)
        Reminders.insert {
            it[reminderId] = id.value
            it[entryId] = reminder.entryId.value
            it[type] = reminder.type
            it[notifyMethods] = checkValidNotifyMethods(reminder.notifyMethods)
            it[message] = reminder.message
            it[spec] = checkValidSpec(reminder.type, reminder.spec)
            it[tz] = checkValidTimeZone(reminder.tz)
            it[status] = reminder.status
            it[dateCreated] = time
            it[dateUpdated] = time
        }
        val created = get(userId, id)
            ?: throw IllegalStateException("Reminder ${id.value} not found after insert")
        log.info("Created reminder, submitting worker request id={}", id.value)
        workerRegistry.acceptReminderWork(ReminderWorkerRequest(userId, created, CrudType.CREATE))
        created
    }

    fun updateReminder(userId: UserId, reminder: NewReminder): Reminder? = transaction {
        if (reminder.reminderId == null) {
            log.info("No reminder id found, defaulting to adding new reminder")
            addReminder(userId, reminder)
        } else if (get(userId, reminder.reminderId) == null) {
            log.info("No reminder found to update id={}", reminder.reminderId)
            null
        } else {
            Reminders.update({ Reminders.reminderId eq reminder.reminderId.value }) {
                it[type] = reminder.type
                it[notifyMethods] = checkValidNotifyMethods(reminder.notifyMethods)
                it[message] = reminder.message
                it[spec] = checkValidSpec(reminder.type, reminder.spec)
                it[tz] = checkValidTimeZone(reminder.tz)
                it[status] = reminder.status
                it[dateUpdated] = OffsetDateTime.now(ZoneOffset.UTC)
            }
            get(userId, reminder.reminderId)?.also {
                log.info("Updated reminder, submitting worker request id={}", reminder.reminderId)
                workerRegistry.acceptReminderWork(ReminderWorkerRequest(userId, it, CrudType.UPDATE))
            }
        }
    }

    fun updateReminderStatus(reminderId: ReminderId, status: ReminderStatus) = transaction {
        Reminders.update({ Reminders.reminderId eq reminderId.value }) {
            it[Reminders.status] = status
        }
    }

    fun delete(userId: UserId, id: ReminderId): Boolean = transaction {
        val reminder = get(userId, id)
        if (reminder != null) {
            Reminders.deleteWhere { Reminders.reminderId eq id.value }
            workerRegistry.acceptReminderWork(ReminderWorkerRequest(userId, reminder, CrudType.DELETE))
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
            throw InvalidModelException("Invalid timezone code: $tz")
        }
    }

    // A bad spec would otherwise only surface when the row is read back or the worker schedules it
    private fun checkValidSpec(type: ReminderType, spec: String): String {
        when (type) {
            ReminderType.ADHOC -> spec.toLongOrNull() ?: throw InvalidModelException("Invalid reminder time: $spec")
            ReminderType.RECURRING -> try {
                Schedule.parse(spec)
            } catch (e: InvalidScheduleException) {
                throw InvalidModelException(e.message ?: "Invalid schedule definition")
            }
        }
        return spec
    }

    private fun checkValidNotifyMethods(methods: List<NotificationMethod>): String {
        if (methods.isEmpty()) throw InvalidModelException("At least one notification method is required")
        return methods.joinToString(",")
    }

}
