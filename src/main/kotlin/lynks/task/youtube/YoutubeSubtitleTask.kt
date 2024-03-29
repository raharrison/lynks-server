package lynks.task.youtube

import lynks.common.TaskParameter
import lynks.common.TaskParameterType
import lynks.common.inject.Inject
import lynks.entry.EntryAuditService
import lynks.entry.LinkService
import lynks.resource.ResourceManager
import lynks.resource.ResourceType
import lynks.resource.WebResourceRetriever
import lynks.task.Task
import lynks.task.TaskBuilder
import lynks.task.TaskContext
import lynks.util.*
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.absolutePathString

class YoutubeSubtitleTask(id: String, entryId: String) : Task<YoutubeSubtitleTask.YoutubeDlSubtitleTaskContext>(id, entryId) {

    private val log = loggerFor<YoutubeSubtitleTask>()

    @Inject
    lateinit var linkService: LinkService

    @Inject
    lateinit var resourceManager: ResourceManager

    @Inject
    lateinit var resourceRetriever: WebResourceRetriever

    @Inject
    lateinit var entryAuditService: EntryAuditService

    override suspend fun process(context: YoutubeDlSubtitleTaskContext) {
        linkService.get(entryId)?.let { link ->
            validateContextUrl(link.url)
            val youtubeDlBinaryPath = YoutubeDlResolver(resourceRetriever).resolveYoutubeDl()
            val tempPath = resourceManager.constructTempBasePath(entryId).resolve("%(title)s")
            val outputTemplate = "-o \"${tempPath.absolutePathString()}\""

            log.info("Executing YoutubeSubtitleTask task entry={}", entryId)
            val command =
                "$youtubeDlBinaryPath --write-sub --write-auto-sub --skip-download --sub-lang en --sub-format ttml $outputTemplate ${link.url}"
            when (val result = ExecUtils.executeCommand(command)) {
                is Result.Success -> {
                    val prefix = "[info] Writing video subtitles to:"
                    val filename = result.value.lines().firstOrNull {
                        it.startsWith(prefix)
                    }?.removePrefix(prefix)?.trim()
                    if (filename != null) {
                        val name = FileUtils.getFileName(filename)
                        log.info("Youtube subtitle task found destination filename={}", filename)
                        val subLines = extractSubtitleText(filename)
                        log.info("Found {} subtitle lines", subLines.size)
                        if (context.searchable) {
                            log.info("Updating link content with subtitles entryId={}", link.id)
                            val content = subLines.joinToString(" ") { it.text.lowercase() }
                            linkService.updateSearchableContent(link.id, content)
                            log.info("Link content successfully updated entryId={}", link.id)
                        }
                        val resourceContent = JsonMapper.defaultMapper.writeValueAsBytes(subLines)
                        resourceManager.saveGeneratedResource(entryId, name, ResourceType.GENERATED, resourceContent)
                        entryAuditService.acceptAuditEvent(
                            entryId, YoutubeSubtitleTask::class.simpleName,
                            "Youtube subtitle download task execution succeeded, created: $name"
                        )
                    } else {
                        log.error("No filename found in output - command likely failed or subtitles not found")
                    }
                }

                is Result.Failure -> {
                    log.error(
                        "Error running YoutubeSubtitleTask task: {} return code: {} error: {}",
                        context.toString(),
                        result.reason.code,
                        result.reason.message
                    )
                    entryAuditService.acceptAuditEvent(
                        entryId, YoutubeSubtitleTask::class.simpleName,
                        "Youtube download task execution failed"
                    )
                }
            }
        }
    }

    private fun extractSubtitleText(filename: String): List<SubtitleLine> {
        val ttml = Files.readString(Path.of(filename)).replace("<br />", " ")
        val builder = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder()
        val doc = builder.parse(ByteArrayInputStream(ttml.toByteArray()))
        val ps = doc.getElementsByTagName("p")
        val subs = mutableListOf<SubtitleLine>()
        for (i in 0 until ps.length) {
            val node = ps.item(i)
            val begin = node.attributes.getNamedItem("begin").textContent
            val end = node.attributes.getNamedItem("end").textContent
            subs.add(SubtitleLine(begin, end, node.textContent))
        }
        return subs
    }

    internal data class SubtitleLine(val begin: String, val end: String, val text: String)

    private fun validateContextUrl(url: String) {
        if (!URLUtils.isValidUrl(url) || URLUtils.extractSource(url) != "youtube.com") {
            throw IllegalArgumentException("Invalid url passed to YoutubeSubtitleTask")
        }
    }

    override fun createContext(params: Map<String, String>) = YoutubeDlSubtitleTaskContext(params)

    companion object {
        fun build(): TaskBuilder {
            val params = listOf(
                TaskParameter(
                    "searchable",
                    TaskParameterType.BOOL,
                    "Search for this entry using retrieved subtitle content",
                    value = "true",
                    required = false
                )
            )
            return TaskBuilder(YoutubeSubtitleTask::class, params)
        }
    }

    class YoutubeDlSubtitleTaskContext(input: Map<String, String>) : TaskContext(input) {
        val searchable: Boolean get() = "true" == ((optParam("searchable")) ?: "true")
    }

}
