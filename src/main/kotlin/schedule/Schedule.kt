package schedule

import common.Entries
import org.jetbrains.exposed.sql.Table
import util.RandomUtils

object Schedules : Table() {
    val scheduleId = varchar("scheduleId", 12).primaryKey()
    val entryId = (varchar("entryId", 12) references Entries.id)
    val type = enumeration("type", ScheduleType::class.java)
    val spec = varchar("spec", 32)
}

enum class ScheduleType {
    DISCUSSION_FINDER, // long
    REMINDER, // date to long
    RECURRING, // string
}

interface Schedule {
    val scheduleId: String
    val entryId: String
    val type: ScheduleType
    val spec: String
}

data class IntervalJob(override val scheduleId: String=RandomUtils.generateUid(), override val entryId: String, override val type: ScheduleType, val interval: Long) : Schedule {
    override val spec: String = interval.toString()
}

data class Reminder(override val scheduleId: String, override val entryId: String, override val type: ScheduleType, val interval: Long) : Schedule {
    override val spec: String = interval.toString()
}

data class RecurringReminder(override val scheduleId: String, override val entryId: String, override val type: ScheduleType, val fire: String) : Schedule {
    override val spec: String = fire
}

data class NewReminder(val scheduleId: String?=null, val entryId: String, val type: ScheduleType, val fire: String)
