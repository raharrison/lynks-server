package lynks.task.youtube

import lynks.common.TaskParameter
import lynks.common.TaskParameterType
import lynks.common.inject.Inject
import lynks.entry.EntryAuditService
import lynks.entry.LinkService
import lynks.resource.GeneratedResource
import lynks.resource.ResourceManager
import lynks.resource.ResourceType
import lynks.resource.WebResourceRetriever
import lynks.task.Task
import lynks.task.TaskBuilder
import lynks.task.TaskContext
import lynks.util.*
import java.io.File
import kotlin.io.path.absolutePathString

class YoutubeDlTask(id: String, entryId: String) : Task<YoutubeDlTask.YoutubeDlTaskContext>(id, entryId) {

    private val log = loggerFor<YoutubeDlTask>()

    @Inject
    lateinit var linkService: LinkService

    @Inject
    lateinit var resourceManager: ResourceManager

    @Inject
    lateinit var resourceRetriever: WebResourceRetriever

    @Inject
    lateinit var entryAuditService: EntryAuditService

    private val outputLogFileMatchers: List<(List<String>) -> String?> = listOf(
        { lines ->
            val suffix = "has already been downloaded and merged"
            lines.lastOrNull { it.endsWith(suffix) }?.removePrefix("[download]")?.removeSuffix(suffix)
        },
        { lines ->
            val suffix = "has already been downloaded"
            lines.lastOrNull { it.endsWith(suffix) }?.removePrefix("[download]")?.removeSuffix(suffix)
        },
        { lines ->
            val prefix = "[ffmpeg] Merging formats into"
            lines.lastOrNull { it.startsWith(prefix) }?.removePrefix(prefix)
        },
        { lines ->
            val prefix = "[download] Destination:"
            lines.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)
        }
    )

    override suspend fun process(context: YoutubeDlTaskContext) {
        linkService.get(entryId)?.let { link ->
            validateContextUrl(link.url)
            val youtubeDlBinaryPath = YoutubeDlResolver(resourceRetriever).resolveYoutubeDl()
            val tempPath = resourceManager.constructTempBasePath(entryId).resolve("%(title)s.f%(format_id)s.%(ext)s")
            val outputTemplate = "-o \"${tempPath.absolutePathString()}\""

            log.info("Executing YoutubeDl task entry={} type={}", entryId, context.type)
            val command = when (context.type) {
                YoutubeDlDownload.BEST_AUDIO -> "$youtubeDlBinaryPath -f \"bestaudio/best\" $outputTemplate ${link.url}"
                YoutubeDlDownload.BEST_VIDEO -> "$youtubeDlBinaryPath -f \"bestvideo[height<=?720]+bestaudio/best\" $outputTemplate ${link.url}"
                YoutubeDlDownload.BEST_VIDEO_TRANSCODE -> "$youtubeDlBinaryPath -f \"bestvideo[height<=?1080]+bestaudio/best\" $outputTemplate ${link.url}"
            }

            when (val result = ExecUtils.executeCommand(command)) {
                is Result.Success -> {
                    val filename =
                        outputLogFileMatchers.firstNotNullOfOrNull { it(result.value.lines()) }?.trim()?.trim('"')
                    if (filename != null) {
                        log.info("YoutubeDl task found destination filename={}", filename)
                        val extension = FileUtils.getExtension(filename)
                        val generatedResources = listOf(GeneratedResource(ResourceType.GENERATED, filename, extension))
                        resourceManager.migrateGeneratedResources(entryId, generatedResources)
                        entryAuditService.acceptAuditEvent(
                            entryId, YoutubeDlTask::class.simpleName,
                            "Youtube download task execution succeeded, created: " + File(filename).name
                        )
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
                    entryAuditService.acceptAuditEvent(
                        entryId, YoutubeDlTask::class.simpleName,
                        "Youtube download task execution failed"
                    )
                }
            }
        }
    }

    private fun validateContextUrl(url: String) {
        if(!URLUtils.isValidUrl(url) || URLUtils.extractSource(url) != "youtube.com") {
            throw IllegalArgumentException("Invalid url passed to YoutubeDlTask")
        }
    }

    override fun createContext(params: Map<String, String>) = YoutubeDlTaskContext(params)

    companion object {
        fun build(): TaskBuilder {
            return TaskBuilder(
                YoutubeDlTask::class,
                listOf(
                    TaskParameter("type", TaskParameterType.ENUM, "Video type",
                        options = YoutubeDlDownload.values().map { it.name }.toSet())
                )
            )
        }
    }

    enum class YoutubeDlDownload {
        BEST_AUDIO,
        BEST_VIDEO,
        BEST_VIDEO_TRANSCODE
    }

    class YoutubeDlTaskContext(input: Map<String, String>) : TaskContext(input) {
        val type: YoutubeDlDownload get() = YoutubeDlDownload.valueOf(param("type"))
    }
}
