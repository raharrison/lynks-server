package lynks.task.youtube

import lynks.common.TaskParameter
import lynks.common.TaskParameterType
import lynks.common.inject.Inject
import lynks.entry.LinkService
import lynks.task.Task
import lynks.task.TaskBuilder
import lynks.task.TaskContext
import lynks.util.loggerFor

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
            youtubeDlRunner.run(entryId, link.url, format)
        }
    }

    override fun createContext(params: Map<String, String>) = YoutubeDlVideoTaskContext(params)

    companion object {
        fun build(): TaskBuilder {
            return TaskBuilder(
                YoutubeDlVideoTask::class,
                listOf(
                    TaskParameter(
                        "type", TaskParameterType.ENUM, "Video type",
                        options = YoutubeDlVideoType.values().map { it.name }.toSet()
                    )
                )
            )
        }
    }

    enum class YoutubeDlVideoType {
        BEST_VIDEO,
        BEST_VIDEO_TRANSCODE
    }

    class YoutubeDlVideoTaskContext(input: Map<String, String>) : TaskContext(input) {
        val type: YoutubeDlVideoType get() = YoutubeDlVideoType.valueOf(param("type"))
    }
}
