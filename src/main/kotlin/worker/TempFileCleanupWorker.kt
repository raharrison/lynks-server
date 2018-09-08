package worker

import common.Environment
import notify.NotifyService
import util.FileUtils
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

class TempFileCleanupWorker(notifyService: NotifyService) : FixedIntervalWorker(notifyService, 6, TimeUnit.HOURS) {

    override suspend fun doWork(input: Boolean) {
        val dirs = FileUtils.directoriesOlderThan(Paths.get(Environment.server.resourceTempPath), 2)
        FileUtils.deleteDirectories(dirs)
    }

}