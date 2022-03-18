package lynks.task.youtube

import lynks.common.TaskParameter
import lynks.common.TaskParameterType
import lynks.common.inject.Inject
import lynks.entry.LinkService
import lynks.task.Task
import lynks.task.TaskBuilder
import lynks.task.TaskContext
import lynks.util.loggerFor
import java.util.*

class YoutubeDlVideoTask(id: String, entryId: String) :
    Task<YoutubeDlVideoTask.YoutubeDlVideoTaskContext>(id, entryId) {

    private val log = loggerFor<YoutubeDlVideoTask>()

    @Inject
    lateinit var linkService: LinkService

    @Inject
    lateinit var youtubeDlRunner: YoutubeDlRunner

    override suspend fun process(context: YoutubeDlVideoTaskContext) {
        linkService.get(entryId)?.let { link ->
            log.info("Executing YoutubeDlVideo task entry={} type={}", entryId, context.type)
            val format = when (context.type) {
                YoutubeDlVideoType.BEST_VIDEO -> "bestvideo[height<=?720]+bestaudio/best"
                YoutubeDlVideoType.BEST_VIDEO_TRANSCODE -> "bestvideo[height<=?1080]+bestaudio/best"
            }
            val optionsBuilder = StringJoiner(" ")
            if (context.sponsorBlock == SponsorBlockOptions.MARK_CHAPTERS) {
                optionsBuilder.add("--sponsorblock-mark all")
            } else if (context.sponsorBlock == SponsorBlockOptions.REMOVE) {
                optionsBuilder.add("--sponsorblock-remove all")
            }
            if (context.startTime != null || context.endTime != null) {
                val postOpts = StringJoiner(" ")
                if (context.startTime != null) {
                    postOpts.add("-ss ${context.startTime}")
                }
                if (context.endTime != null) {
                    postOpts.add("-to ${context.endTime}")
                }
                optionsBuilder.add("--postprocessor-args=\"$postOpts\"")
            }
            youtubeDlRunner.run(entryId, link.url, format, optionsBuilder.toString())
        }
    }

    override fun createContext(params: Map<String, String>) = YoutubeDlVideoTaskContext(params)

    companion object {
        fun build(): TaskBuilder {
            val params = listOf(
                TaskParameter(
                    "type", TaskParameterType.ENUM, "Video type",
                    options = YoutubeDlVideoType.values().map { it.name }.toList()
                ),
                TaskParameter(
                    "startTime", TaskParameterType.TEXT, "Start Time (00:00:00)", required = false
                ),
                TaskParameter(
                    "endTime", TaskParameterType.TEXT, "End Time (00:00:00)", required = false
                ),
                TaskParameter(
                    "sponsorBlock", TaskParameterType.ENUM, "SponsorBlock",
                    options = SponsorBlockOptions.values().map { it.name }.toList()
                ),
            )
            return TaskBuilder(YoutubeDlVideoTask::class, params)
        }
    }

    enum class YoutubeDlVideoType {
        BEST_VIDEO,
        BEST_VIDEO_TRANSCODE
    }

    class YoutubeDlVideoTaskContext(input: Map<String, String>) : TaskContext(input) {
        init {
            TimeSeekValidator.validateStartTime(startTime)
            TimeSeekValidator.validateEndTime(endTime)
        }
        val type: YoutubeDlVideoType get() = YoutubeDlVideoType.valueOf(param("type"))
        val startTime: String? get() = optParam("startTime")
        val endTime: String? get() = optParam("endTime")
        val sponsorBlock: SponsorBlockOptions get() = SponsorBlockOptions.valueOf(param("sponsorBlock"))
    }
}
