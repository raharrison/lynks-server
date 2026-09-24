package lynks.reminder

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
import kotlin.math.max

class ReminderService(private val workerRegistry: WorkerRegistry) {

    private val log = loggerFor<ReminderService>()

    private fun toModel(row: ResultRow): Reminder {
        return when (row[Reminders.type]) {
            ReminderType.ADHOC -> AdhocReminder(
                ReminderId(row[Reminders.reminderId]), EntryId(row[Reminders.entryId]), toNotifyMethods(row[Reminders.notifyMethods]),
                    row[Reminders.message], row[Reminders.spec].toLong(), row[Reminders.tz], row[Reminders.status],
                row[Reminders.dateCreated].toInstant(),
                row[Reminders.dateUpdated].toInstant(),
                row[Entries.type],
                row[Entries.title]
            )
            ReminderType.RECURRING -> RecurringReminder(
                    ReminderId(row[Reminders.reminderId]), EntryId(row[Reminders.entryId]), toNotifyMethods(row[Reminders.notifyMethods]),
                row[Reminders.message],
                Schedule.fromSpec(row[Reminders.spec]),
                row[Reminders.tz],
                row[Reminders.status],
                row[Reminders.dateCreated].toInstant(),
                row[Reminders.dateUpdated].toInstant(),
                row[Entries.type],
                row[Entries.title]
            )
        }
    }

    // convert stored comma-separated set of methods to list of enum
    private fun toNotifyMethods(str: String): List<NotificationMethod> {
        return str.split(',').map { NotificationMethod.valueOf(it) }
    }

    private fun owned(userId: UserId): Op<Boolean> = Entries.userId eq userId.value

    private val reminderQuerySlice = Reminders.columns + listOf(Entries.type, Entries.title)

    private fun ownedQuery(userId: UserId) = Reminders.innerJoin(Entries).select(reminderQuerySlice).where { owned(userId) }

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
        Reminders.innerJoin(Entries).select(reminderQuerySlice + Entries.userId)
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
            it[spec] = checkValidSpec(reminder)
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
                it[spec] = checkValidSpec(reminder)
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

    fun previewSchedule(schedule: Schedule, tz: String, count: Int = 5): List<OffsetDateTime> {
        checkValidSchedule(schedule)
        val fires = mutableListOf<OffsetDateTime>()
        var next = schedule.next(ZonedDateTime.now(ZoneId.of(checkValidTimeZone(tz))))
        while (next != null && fires.size < count) {
            fires += next.toOffsetDateTime()
            next = schedule.next(next)
        }
        return fires
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
    private fun checkValidSpec(reminder: NewReminder): String = when (reminder.type) {
        ReminderType.ADHOC -> (reminder.fireAt ?: throw InvalidModelException("An adhoc reminder needs a fire time")).toString()
        ReminderType.RECURRING -> checkValidSchedule(
            reminder.schedule ?: throw InvalidModelException("A recurring reminder needs a schedule")
        ).toSpec()
    }

    private fun checkValidSchedule(schedule: Schedule): Schedule {
        schedule.validate()
        schedule.next(ZonedDateTime.now(ZoneOffset.UTC)) ?: throw InvalidModelException("This schedule never fires")
        return schedule
    }

    private fun checkValidNotifyMethods(methods: List<NotificationMethod>): String {
        if (methods.isEmpty()) throw InvalidModelException("At least one notification method is required")
        return methods.joinToString(",")
    }

}
