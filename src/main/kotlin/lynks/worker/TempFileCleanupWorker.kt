package lynks.worker

import kotlinx.coroutines.time.delay
import lynks.common.Environment
import lynks.entry.EntryAuditService
import lynks.notify.NotifyService
import lynks.user.Preferences
import lynks.user.UserService
import lynks.util.FileUtils
import java.nio.file.Paths
import java.time.Duration

class TempFileCleanupWorkerRequest(val preferences: Preferences, crudType: CrudType = CrudType.UPDATE) :
    VariableWorkerRequest(crudType) {
    override fun hashCode(): Int = 1
    override fun equals(other: Any?): Boolean = other is TempFileCleanupWorkerRequest
}

private const val MAX_FILE_AGE = 14L // days

class TempFileCleanupWorker(
    private val userService: UserService,
    notifyService: NotifyService,
    entryAuditService: EntryAuditService
) : VariableChannelBasedWorker<TempFileCleanupWorkerRequest>(notifyService, entryAuditService) {

    override suspend fun beforeWork() {
        val preferences = userService.currentUserPreferences
        this.onChannelReceive(TempFileCleanupWorkerRequest(preferences, CrudType.CREATE))
    }

    override suspend fun doWork(input: TempFileCleanupWorkerRequest) {
        val sleep = input.preferences.tempFileCleanInterval
        val sleepDuration = Duration.ofHours(sleep)

        // initial delay from startup
        delay(sleepDuration)

        while (true) {
            try {
                val dirs = FileUtils.directoriesOlderThan(Paths.get(Environment.resource.resourceTempPath), MAX_FILE_AGE)
                if(dirs.isNotEmpty()) {
                    log.info("Temp file cleanup worker removing {} dirs: {}", dirs.size, dirs)
                    FileUtils.deleteDirectories(dirs)
                }
            } finally {
                log.info("Temp file cleanup worker sleeping for {} hours", sleep)
                delay(sleepDuration)
            }
        }
    }

}
