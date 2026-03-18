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


/*


## Not Persisted

Simple channel based

#### Link Processing Worker
Input = PersistLinkProcessingRequest (Link)
- attached to entryId
- adhoc


#### TaskRunnerWorker
Input = Task + TaskContext
- adhoc







#### Reminder Worker

Input = ReminderRequest (Reminder)

- attached to reminderId



## Persisted

### Can be updated by user


#### UnreadLinkDigestWorker + TempFileCleanupWorker
Input = Preferences
- attached to user preferences

-


### Updated by worker


#### Discussion Finder Worker

Input = Link

- attached to entryId
- takes interval
- update within worker


 */
