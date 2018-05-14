package task

import com.github.kittinunf.result.Result
import common.inject.Inject
import resource.ResourceManager
import resource.ResourceType
import util.ExecUtils
import java.io.File

class YoutubeDlTask(id: String, entryId: String) : Task<YoutubeDlTask.YoutubeDlTaskContext>(id, entryId) {

    @Inject
    lateinit var resourceManager: ResourceManager

    override suspend fun process(context: YoutubeDlTaskContext) {
        val outputTemplate = "-o ${resourceManager.constructPath(entryId, "%(title)s.%(ext)s")}"

        val command = when(context.type) {
            YoutubeDlDownload.BEST_AUDIO -> "youtube-dl -f bestaudio/best $outputTemplate ${context.url}"
            YoutubeDlDownload.BEST_VIDEO -> "youtube-dl -f best $outputTemplate ${context.url}"
            YoutubeDlDownload.BEST_VIDEO_TRANSCODE -> "youtube-dl -f bestvideo[height<=?1080]+bestaudio/best $outputTemplate ${context.url}"
        }

        val result = ExecUtils.executeCommand(command)

        when (result) {
            is Result.Success -> {
                // find destination
                val prefix = "[download] Destination:"
                val filename = result.value.lines().single {
                    it.startsWith(prefix)
                }

                val file = File(filename.removePrefix(prefix))
                resourceManager.saveGeneratedResource(
                        entryId = entryId,
                        name = file.name,
                        format = file.extension,
                        size = file.length(),
                        type = ResourceType.UPLOAD)
            }
        }
    }

    override fun createContext(input: Map<String, String>): YoutubeDlTaskContext {
        return YoutubeDlTaskContext(input)
    }

    companion object {
        fun build(url: String, type: YoutubeDlDownload): TaskBuilder {
            return TaskBuilder(YoutubeDlTask::class, YoutubeDlTask.YoutubeDlTaskContext(url, type))
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