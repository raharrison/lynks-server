package lynks.worker

import kotlinx.coroutines.time.delay
import lynks.common.Environment
import lynks.util.FileUtils
import java.nio.file.Paths
import java.time.Duration
import kotlin.io.path.exists

class TempFileCleanupWorkerRequest(val maxFileAgeDays: Int, val intervalHours: Int) {
    override fun hashCode(): Int = 1
    override fun equals(other: Any?): Boolean = other is TempFileCleanupWorkerRequest
}

class TempFileCleanupWorker : ChannelBasedWorker<TempFileCleanupWorkerRequest>() {

    override suspend fun beforeWork() {
        val maxFileAge = Environment.resource.maxTempResourceAge
        val intervalHours = Environment.resource.tempFileCleanInterval
        super.onChannelReceive(TempFileCleanupWorkerRequest(maxFileAge, intervalHours))
    }

    override suspend fun doWork(input: TempFileCleanupWorkerRequest) {
        val maxFileAge = input.maxFileAgeDays.toLong()
        val sleepHours = input.intervalHours.toLong()
        val sleepDuration = Duration.ofHours(sleepHours)

        // initial delay from startup
        delay(sleepDuration)

        while (true) {
            try {
                val tempPath = Paths.get(Environment.resource.resourceTempPath)
                if (tempPath.exists()) {
                    val dirs = FileUtils.directoriesOlderThan(tempPath, maxFileAge)
                    if (dirs.isNotEmpty()) {
                        log.info("Temp file cleanup worker removing {} dirs: {}", dirs.size, dirs)
                        FileUtils.deleteDirectories(dirs)
                    }
                }
            } finally {
                log.info("Temp file cleanup worker sleeping for {} hours", sleepHours)
                delay(sleepDuration)
            }
        }
    }

}
