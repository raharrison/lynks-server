package worker

import resource.ResourceManager
import util.FileUtils
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

class TempFileCleanupWorker: ScheduledWorker(6, TimeUnit.HOURS) {

    override fun doWork() {
        val dirs = FileUtils.directoriesOlderThan(Paths.get(ResourceManager.TEMP_PATH), 2)
        FileUtils.deleteDirectories(dirs)
    }

}