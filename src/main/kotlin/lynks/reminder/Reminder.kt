package lynks.reminder

import lynks.common.Entries
import lynks.notify.NotificationMethod
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object Reminders : Table("REMINDER") {
    val reminderId = varchar("REMINDER_ID", 14)
    val entryId = (varchar("ENTRY_ID", 14).references(Entries.id, ReferenceOption.CASCADE))
    val type = enumeration("TYPE", ReminderType::class)
    val notifyMethod = enumeration("NOTIFY_METHOD", NotificationMethod::class)
    val message = varchar("MESSAGE", 255).nullable()
    val spec = varchar("SPEC", 32)
    val tz = varchar("TZ", 32)
    val dateCreated = long("DATE_CREATED")
    val dateUpdated = long("DATE_UPDATED")
    override val primaryKey = PrimaryKey(reminderId)
}

enum class ReminderType {
    ADHOC, // date to long
    RECURRING, // string
}

interface Reminder {
    val reminderId: String
    val entryId: String
    val type: ReminderType
    val notifyMethod: NotificationMethod
    val message: String?
    val spec: String
    val tz: String
    val dateCreated: Long
    val dateUpdated: Long
}

data class AdhocReminder(override val reminderId: String,
                         override val entryId: String,
                         override val notifyMethod: NotificationMethod,
                         override val message: String?,
                         val interval: Long,
                         override val tz: String,
                         override val dateCreated: Long,
                         override val dateUpdated: Long) : Reminder {
    override val type: ReminderType = ReminderType.ADHOC
    override val spec: String = interval.toString()
}

data class RecurringReminder(override val reminderId: String,
                             override val entryId: String,
                             override val notifyMethod: NotificationMethod,
                             override val message: String?,
                             val fire: String,
                             override val tz: String,
                             override val dateCreated: Long,
                             override val dateUpdated: Long) : Reminder {
    override val type: ReminderType = ReminderType.RECURRING
    override val spec: String = fire
}

data class NewReminder(val reminderId: String? = null, val entryId: String, val type: ReminderType,
                       val notifyMethod: NotificationMethod, val message: String? = null, val spec: String, val tz: String)
