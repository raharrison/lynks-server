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

class YoutubeDlAudioTask(id: String, entryId: String) :
    Task<YoutubeDlAudioTask.YoutubeDlAudioTaskContext>(id, entryId) {

    private val log = loggerFor<YoutubeDlAudioTask>()

    @Inject
    lateinit var linkService: LinkService

    @Inject
    lateinit var youtubeDlRunner: YoutubeDlRunner

    override suspend fun process(context: YoutubeDlAudioTaskContext) {
        linkService.get(entryId)?.let { link ->
            log.info("Executing YoutubeDlAudio task entry={} type={}", entryId, context.type)
            val optionsBuilder = StringJoiner(" ")
            val format = when (context.type) {
                YoutubeDlAudioType.BEST_AUDIO -> "bestaudio/best"
                YoutubeDlAudioType.BEST_MP3 -> {
                    optionsBuilder.add("--extract-audio --audio-format mp3 --audio-quality 0")
                    "bestaudio/best"
                }
            }
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

    override fun createContext(params: Map<String, String>) = YoutubeDlAudioTaskContext(params)

    companion object {
        fun build(): TaskBuilder {
            val params = listOf(
                TaskParameter(
                    "type", TaskParameterType.ENUM, "Audio type",
                    options = YoutubeDlAudioType.values().map { it.name }.toSet()
                ),
                TaskParameter(
                    "startTime", TaskParameterType.TEXT, "Start Time (00:00:00)", required = false
                ),
                TaskParameter(
                    "endTime", TaskParameterType.TEXT, "End Time (00:00:00)", required = false
                ),
                TaskParameter(
                    "sponsorBlock", TaskParameterType.ENUM, "SponsorBlock",
                    options = SponsorBlockOptions.values().map { it.name }.toSet()
                ),
            )
            return TaskBuilder(YoutubeDlAudioTask::class, params)
        }
    }

    enum class YoutubeDlAudioType {
        BEST_AUDIO,
        BEST_MP3
    }

    class YoutubeDlAudioTaskContext(input: Map<String, String>) : TaskContext(input) {
        init {
            TimeSeekValidator.validateStartTime(startTime)
            TimeSeekValidator.validateEndTime(endTime)
        }
        val type: YoutubeDlAudioType get() = YoutubeDlAudioType.valueOf(param("type"))
        val startTime: String? get() = optParam("startTime")
        val endTime: String? get() = optParam("endTime")
        val sponsorBlock: SponsorBlockOptions get() = SponsorBlockOptions.valueOf(param("sponsorBlock"))
    }
}
