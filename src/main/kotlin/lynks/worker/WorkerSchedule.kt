package lynks.worker

import org.jetbrains.exposed.sql.Table

object WorkerSchedules: Table("WORKER_SCHEDULE") {

    val worker = varchar("WORKER", 50).index()
    val key = varchar("KEY", 20).nullable()
    val request = varchar("REQUEST", 255)
    val lastRun = long("LAST_RUN").nullable()
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
