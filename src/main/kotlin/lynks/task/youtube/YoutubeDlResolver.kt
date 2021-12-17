package lynks.task.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lynks.common.Environment
import lynks.resource.WebResourceRetriever
import lynks.util.FileUtils
import lynks.util.Result
import lynks.util.loggerFor
import org.apache.commons.lang3.SystemUtils
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

class YoutubeDlResolver(private val resourceRetriever: WebResourceRetriever) {

    private val log = loggerFor<YoutubeDlResolver>()

    suspend fun resolveYoutubeDl(): String {
        val binaryName = "youtube-dl${if (SystemUtils.IS_OS_WINDOWS) ".exe" else ""}"
        val binaryPath = Paths.get(Environment.resource.binaryBasePath, binaryName)
        if (binaryPath.exists()) {
            log.info("Youtube-dl binary resolved to {}", binaryPath)
        } else {
            val youtubeDlHost = Environment.external.youtubeDlHost
            log.info("No youtube-dl binary found, retrieving from: {}", youtubeDlHost)
            when (val result = resourceRetriever.getFileResult("${youtubeDlHost}/${binaryName}")) {
                is Result.Failure -> throw result.reason
                is Result.Success -> {
                    val bytes = result.value
                    withContext(Dispatchers.IO) {
                        FileUtils.writeToFile(binaryPath, bytes)
                        binaryPath.toFile().setExecutable(true)
                    }
                    log.info("Youtube-dl binary successfully saved to {}", binaryPath)
                }
            }
        }
        return binaryPath.absolutePathString()
    }

}
