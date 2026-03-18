package lynks.reminder

import lynks.common.Entries
import lynks.common.EntryId
import lynks.common.ReminderId
import lynks.common.UID_LENGTH
import lynks.notify.NotificationMethod
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.Instant

object Reminders : Table("reminders") {
    val reminderId = varchar("reminder_id", UID_LENGTH)
    val entryId = (varchar("entry_id", UID_LENGTH).references(Entries.id, ReferenceOption.CASCADE)).index()
    val type = enumerationByName<ReminderType>("reminder_type", 20)
    val notifyMethods = varchar("notify_methods", 64)
    val message = varchar("message", 255).nullable()
    val spec = varchar("spec", 255)
    val tz = varchar("tz", 64)
    val status = enumerationByName<ReminderStatus>("status", 20)
    val dateCreated = timestampWithTimeZone("date_created")
    val dateUpdated = timestampWithTimeZone("date_updated")
    override val primaryKey = PrimaryKey(reminderId)
}

enum class ReminderType {
    ADHOC, // date to long
    RECURRING, // string
}

enum class ReminderStatus {
    ACTIVE,
    COMPLETED,
    DISABLED
}

interface Reminder {
    val reminderId: ReminderId
    val entryId: EntryId
    val type: ReminderType
    val notifyMethods: List<NotificationMethod>
    val message: String?
    val spec: String
    val tz: String
    val status: ReminderStatus
    val dateCreated: Instant
    val dateUpdated: Instant
}

data class AdhocReminder(override val reminderId: ReminderId,
                         override val entryId: EntryId,
                         override val notifyMethods: List<NotificationMethod>,
                         override val message: String?,
                         val interval: Long,
                         override val tz: String,
                         override val status: ReminderStatus,
                         override val dateCreated: Instant,
                         override val dateUpdated: Instant) : Reminder {
    override val type: ReminderType = ReminderType.ADHOC
    override val spec: String = interval.toString()
}

data class RecurringReminder(override val reminderId: ReminderId,
                             override val entryId: EntryId,
                             override val notifyMethods: List<NotificationMethod>,
                             override val message: String?,
                             val fire: String,
                             override val tz: String,
                             override val status: ReminderStatus,
                             override val dateCreated: Instant,
                             override val dateUpdated: Instant) : Reminder {
    override val type: ReminderType = ReminderType.RECURRING
    override val spec: String = fire
}

data class NewReminder(val reminderId: ReminderId? = null, val entryId: EntryId, val type: ReminderType,
                       val notifyMethods: List<NotificationMethod>, val message: String? = null,
                       val spec: String, val tz: String, val status: ReminderStatus)
