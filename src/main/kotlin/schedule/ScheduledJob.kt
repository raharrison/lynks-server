package schedule

import common.Entries
import org.jetbrains.exposed.sql.Table

object ScheduledJobs : Table() {
    val entryId = (varchar("entryId", 12) references Entries.id).primaryKey()
    val type = enumeration("type", ScheduleType::class.java).primaryKey()
    val interval = long("interval")
}

enum class ScheduleType {
    DISCUSSION_FINDER
}
