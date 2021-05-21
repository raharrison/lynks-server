package task

import common.Environment
import common.inject.Inject
import entry.EntryAuditService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.SystemUtils
import resource.ResourceManager
import resource.ResourceRetriever
import resource.ResourceType
import util.ExecUtils
import util.FileUtils
import util.Result
import util.loggerFor
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

class YoutubeDlTask(id: String, entryId: String) : Task<YoutubeDlTask.YoutubeDlTaskContext>(id, entryId) {

    private val log = loggerFor<YoutubeDlTask>()

    @Inject
    lateinit var resourceManager: ResourceManager

    @Inject
    lateinit var resourceRetriever: ResourceRetriever

    @Inject
    lateinit var entryAuditService: EntryAuditService

    override suspend fun process(context: YoutubeDlTaskContext) {
        val youtubeDlBinaryPath = resolveYoutubeDl()
        val outputTemplate = "-o \"${resourceManager.constructPath(entryId, "%(name)s.%(ext)s")}\""

        log.info("Executing YoutubeDl task entry={} type={}", entryId, context.type)
        val command = when (context.type) {
            YoutubeDlDownload.BEST_AUDIO -> "$youtubeDlBinaryPath -f bestaudio/best $outputTemplate ${context.url}"
            YoutubeDlDownload.BEST_VIDEO -> "$youtubeDlBinaryPath -f best $outputTemplate ${context.url}"
            YoutubeDlDownload.BEST_VIDEO_TRANSCODE -> "$youtubeDlBinaryPath -f bestvideo[height<=?1080]+bestaudio/best $outputTemplate ${context.url}"
        }

        when (val result = ExecUtils.executeCommand(command)) {
            is Result.Success -> {
                // find destination
                val prefix = "[download] Destination:"
                val filename = result.value.lines().singleOrNull {
                    it.startsWith(prefix)
                }
                // error or file already exists
                if (filename != null) {
                    log.debug("YoutubeDl task found destination filename={}", filename)
                    val file = File(filename.removePrefix(prefix).trim())
                    resourceManager.saveGeneratedResource(
                            entryId = entryId,
                            type = ResourceType.GENERATED,
                            path = file.toPath()
                    )
                    entryAuditService.acceptAuditEvent(entryId, YoutubeDlTask::class.simpleName,
                        "Youtube download task execution succeeded, created: " + file.name)
                } else {
                    log.error("No filename found in YoutubeDl output - command likely failed")
                }
            }
            is Result.Failure -> {
                log.error(
                    "Error running YoutubeDl task: {} return code: {} error: {}",
                    context.toString(),
                    result.reason.code,
                    result.reason.message
                )
                entryAuditService.acceptAuditEvent(entryId, YoutubeDlTask::class.simpleName,
                    "Youtube download task execution failed")
            }
        }
    }

    private suspend fun resolveYoutubeDl(): String {
        val binaryName = "youtube-dl${if(SystemUtils.IS_OS_WINDOWS) ".exe" else ""}"
        val binaryPath = Paths.get(Environment.resource.binaryBasePath, binaryName)
        if(binaryPath.exists()) {
            log.info("Youtube-dl binary resolved to {}", binaryPath)
        } else {
            val youtubeDlHost = Environment.external.youtubeDlHost
            log.info("No youtube-dl binary found, retrieving from: {}", youtubeDlHost)
            when(val result = resourceRetriever.getFileResult("${youtubeDlHost}/${binaryName}")) {
                is Result.Failure -> throw result.reason
                is Result.Success -> {
                    val bytes = result.value
                    withContext(Dispatchers.IO) {
                        FileUtils.writeToFile(binaryPath, bytes)
                    }
                    log.info("Youtube-dl binary successfully saved to {}", binaryPath)
                }
            }
        }
        return binaryPath.absolutePathString()
    }

    override fun createContext(input: Map<String, String>): YoutubeDlTaskContext {
        return YoutubeDlTaskContext(input)
    }

    companion object {
        fun build(url: String, type: YoutubeDlDownload): TaskBuilder {
            return TaskBuilder(YoutubeDlTask::class, YoutubeDlTaskContext(url, type))
        }
    }

    enum class YoutubeDlDownload {
        BEST_AUDIO,
        BEST_VIDEO,
        BEST_VIDEO_TRANSCODE
    }

    class YoutubeDlTaskContext(input: Map<String, String>) : TaskContext(input) {

        constructor(url: String, type: YoutubeDlDownload) : this(mapOf("url" to url, "type" to type.toString()))

        val type: YoutubeDlDownload get() = YoutubeDlDownload.valueOf(param("type"))

        val url: String get() = param("url")

    }
}
