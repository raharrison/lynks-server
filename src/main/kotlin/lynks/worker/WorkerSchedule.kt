package lynks.worker

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object WorkerSchedules: Table("worker_schedules") {

    val worker = varchar("worker", 50)
    val key = varchar("key", 20).nullable()
    val request = text("request")
    val lastRun = timestampWithTimeZone("last_run").nullable()
    override val primaryKey = PrimaryKey(worker, key)
}
