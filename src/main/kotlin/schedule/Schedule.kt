package schedule

import common.Entries
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object Schedules : Table("Schedule") {
    val scheduleId = varchar("scheduleId", 12).primaryKey()
    val entryId = (varchar("entryId", 12).references(Entries.id, ReferenceOption.CASCADE))
    val type = enumeration("type", ScheduleType::class.java)
    val spec = varchar("spec", 32)
    val tz = varchar("tz", 32)
}

enum class ScheduleType {
    REMINDER, // date to long
    RECURRING, // string
}

interface Schedule {
    val scheduleId: String
    val entryId: String
    val type: ScheduleType
    val spec: String
    val tz: String
}

data class Reminder(override val scheduleId: String,
                    override val entryId: String,
                    override val type: ScheduleType,
                    val interval: Long,
                    override val tz: String) : Schedule {
    override val spec: String = interval.toString()
}

data class RecurringReminder(override val scheduleId: String,
                             override val entryId: String,
                             override val type: ScheduleType,
                             val fire: String,
                             override val tz: String) : Schedule {
    override val spec: String = fire
}

data class NewReminder(val scheduleId: String?=null, val entryId: String, val type: ScheduleType, val spec: String, val tz: String)
