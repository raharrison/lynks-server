package lynks.task.youtube

import lynks.entry.EntryAuditService
import lynks.resource.GeneratedResource
import lynks.resource.ResourceManager
import lynks.resource.ResourceType
import lynks.resource.WebResourceRetriever
import lynks.util.*
import java.io.File
import kotlin.io.path.absolutePathString

class YoutubeDlRunner(
    private val resourceRetriever: WebResourceRetriever,
    private val resourceManager: ResourceManager,
    private val entryAuditService: EntryAuditService
) {

    private val log = loggerFor<YoutubeDlRunner>()

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
            val prefix = "[Merger] Merging formats into"
            lines.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)
        },
        { lines ->
            val prefix = "[ffmpeg] Merging formats into"
            lines.lastOrNull { it.startsWith(prefix) }?.removePrefix(prefix)
        },
        { lines ->
            val prefix = "[download] Destination:"
            lines.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)
        },
        { lines ->
            val prefix = "[ExtractAudio] Destination:"
            lines.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)
        }
    )

    suspend fun run(entryId: String, url: String, format: String, options: String = "") {
        validateVideoUrl(url)
        val youtubeDlBinaryPath = YoutubeDlResolver(resourceRetriever).resolveYoutubeDl()
        val tempPath = resourceManager.constructTempBasePath(entryId).resolve("%(title)s.f%(format_id)s.%(ext)s")
        val outputTemplate = "-o \"${tempPath.absolutePathString()}\""

        val command = "$youtubeDlBinaryPath $options -f \"$format\" $outputTemplate $url"

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
                        entryId, "YoutubeDlTask",
                        "Youtube download task execution succeeded, created: " + File(filename).name
                    )
                } else {
                    log.error("No filename found in YoutubeDl output - command likely failed")
                }
            }
            is Result.Failure -> {
                log.error(
                    "Error running YoutubeDlTask return code: {} error: {}",
                    result.reason.code,
                    result.reason.message
                )
                entryAuditService.acceptAuditEvent(
                    entryId, "YoutubeDlTask",
                    "Youtube download task execution failed"
                )
            }
        }
    }

    private fun validateVideoUrl(url: String) {
        if (!URLUtils.isValidUrl(url) || URLUtils.extractSource(url) != "youtube.com") {
            throw IllegalArgumentException("Invalid url passed to YoutubeDlTask")
        }
    }

}
