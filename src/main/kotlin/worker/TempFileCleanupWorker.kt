package worker

import common.Environment
import kotlinx.coroutines.time.delay
import notify.NotifyService
import user.Preferences
import user.UserService
import util.FileUtils
import java.nio.file.Paths
import java.time.Duration

class TempFileCleanupWorkerRequest(val preferences: Preferences, crudType: CrudType = CrudType.UPDATE): VariableWorkerRequest(crudType) {
    override fun hashCode(): Int = 1
    override fun equals(other: Any?): Boolean = other is TempFileCleanupWorkerRequest
}

private const val MAX_FILE_AGE = 14L // days

class TempFileCleanupWorker(private val userService: UserService, notifyService: NotifyService) : VariableChannelBasedWorker<TempFileCleanupWorkerRequest>(notifyService) {

    override suspend fun beforeWork() {
        val preferences = userService.currentUserPreferences
        this.onChannelReceive(TempFileCleanupWorkerRequest(preferences, CrudType.CREATE))
    }

    override suspend fun doWork(input: TempFileCleanupWorkerRequest) {
        while(true) {
            try {
                val dirs = FileUtils.directoriesOlderThan(Paths.get(Environment.server.resourceTempPath), MAX_FILE_AGE)
                FileUtils.deleteDirectories(dirs)
            } finally {
                delay(Duration.ofHours(input.preferences.tempFileCleanInterval))
            }
        }
    }

}