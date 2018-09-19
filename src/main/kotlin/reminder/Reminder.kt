package reminder

import common.Entries
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object Reminders : Table("Reminder") {
    val reminderId = varchar("reminderId", 12).primaryKey()
    val entryId = (varchar("entryId", 12).references(Entries.id, ReferenceOption.CASCADE))
    val type = enumeration("type", ReminderType::class)
    val message = varchar("message", 255).nullable()
    val spec = varchar("spec", 32)
    val tz = varchar("tz", 32)
}

enum class ReminderType {
    ADHOC, // date to long
    RECURRING, // string
}

interface Reminder {
    val reminderId: String
    val entryId: String
    val type: ReminderType
    val message: String?
    val spec: String
    val tz: String
}

data class AdhocReminder(override val reminderId: String,
                         override val entryId: String,
                         override val message: String?,
                         val interval: Long,
                         override val tz: String) : Reminder {
    override val type: ReminderType = ReminderType.ADHOC
    override val spec: String = interval.toString()
}

data class RecurringReminder(override val reminderId: String,
                             override val entryId: String,
                             override val message: String?,
                             val fire: String,
                             override val tz: String) : Reminder {
    override val type: ReminderType = ReminderType.RECURRING
    override val spec: String = fire
}

data class NewReminder(val reminderId: String? = null, val entryId: String, val type: ReminderType, val message: String? = null, val spec: String, val tz: String)
