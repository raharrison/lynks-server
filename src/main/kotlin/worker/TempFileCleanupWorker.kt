package worker

import common.Environment
import util.FileUtils
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

class TempFileCleanupWorker: ScheduledWorker(6, TimeUnit.HOURS) {

    override fun doWork() {
        val dirs = FileUtils.directoriesOlderThan(Paths.get(Environment.server.resourceTempPath), 2)
        FileUtils.deleteDirectories(dirs)
    }

}