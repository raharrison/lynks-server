package lynks.reminder

import lynks.common.Entries
import lynks.common.UID_LENGTH
import lynks.notify.NotificationMethod
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object Reminders : Table("REMINDER") {
    val reminderId = varchar("REMINDER_ID", UID_LENGTH)
    val entryId = (varchar("ENTRY_ID", UID_LENGTH).references(Entries.id, ReferenceOption.CASCADE)).index()
    val type = enumeration("REMINDER_TYPE", ReminderType::class)
    val notifyMethods = varchar("NOTIFY_METHODS", 16)
    val message = varchar("MESSAGE", 255).nullable()
    val spec = varchar("SPEC", 64)
    val tz = varchar("TZ", 32)
    val status = enumeration("STATUS", ReminderStatus::class)
    val dateCreated = long("DATE_CREATED")
    val dateUpdated = long("DATE_UPDATED")
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
    val reminderId: String
    val entryId: String
    val type: ReminderType
    val notifyMethods: List<NotificationMethod>
    val message: String?
    val spec: String
    val tz: String
    val status: ReminderStatus
    val dateCreated: Long
    val dateUpdated: Long
}

data class AdhocReminder(override val reminderId: String,
                         override val entryId: String,
                         override val notifyMethods: List<NotificationMethod>,
                         override val message: String?,
                         val interval: Long,
                         override val tz: String,
                         override val status: ReminderStatus,
                         override val dateCreated: Long,
                         override val dateUpdated: Long) : Reminder {
    override val type: ReminderType = ReminderType.ADHOC
    override val spec: String = interval.toString()
}

data class RecurringReminder(override val reminderId: String,
                             override val entryId: String,
                             override val notifyMethods: List<NotificationMethod>,
                             override val message: String?,
                             val fire: String,
                             override val tz: String,
                             override val status: ReminderStatus,
                             override val dateCreated: Long,
                             override val dateUpdated: Long) : Reminder {
    override val type: ReminderType = ReminderType.RECURRING
    override val spec: String = fire
}

data class NewReminder(val reminderId: String? = null, val entryId: String, val type: ReminderType,
                       val notifyMethods: List<NotificationMethod>, val message: String? = null,
                       val spec: String, val tz: String, val status: ReminderStatus)
