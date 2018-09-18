package worker

import org.jetbrains.exposed.sql.Table

object WorkerSchedules: Table("WorkerSchedule") {

    val worker = varchar("worker", 50).index()
    val key = varchar("key", 10).nullable()
    val request = varchar("request", 255)
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