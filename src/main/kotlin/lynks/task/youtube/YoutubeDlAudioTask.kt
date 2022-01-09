package lynks.task.youtube

import lynks.common.TaskParameter
import lynks.common.TaskParameterType
import lynks.common.inject.Inject
import lynks.entry.LinkService
import lynks.task.Task
import lynks.task.TaskBuilder
import lynks.task.TaskContext
import lynks.util.loggerFor

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
            val format = when (context.type) {
                YoutubeDlAudioType.BEST_AUDIO -> "bestaudio/best"
            }
            youtubeDlRunner.run(entryId, link.url, format)
        }
    }

    override fun createContext(params: Map<String, String>) = YoutubeDlAudioTaskContext(params)

    companion object {
        fun build(): TaskBuilder {
            return TaskBuilder(
                YoutubeDlAudioTask::class,
                listOf(
                    TaskParameter(
                        "type", TaskParameterType.ENUM, "Audio type",
                        options = YoutubeDlAudioType.values().map { it.name }.toSet()
                    )
                )
            )
        }
    }

    enum class YoutubeDlAudioType {
        BEST_AUDIO
    }

    class YoutubeDlAudioTaskContext(input: Map<String, String>) : TaskContext(input) {
        val type: YoutubeDlAudioType get() = YoutubeDlAudioType.valueOf(param("type"))
    }
}
