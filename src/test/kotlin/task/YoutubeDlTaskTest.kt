package task

import entry.EntryAuditService
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import resource.Resource
import resource.ResourceManager
import resource.ResourceType
import util.ExecException
import util.ExecUtils
import util.Result
import java.nio.file.Paths

class YoutubeDlTaskTest {

    private val resourceManager = mockk<ResourceManager>()
    private val entryAuditService = mockk<EntryAuditService>(relaxUnitFun = true)

    private val youtubeDlTask = YoutubeDlTask("tid", "eid").also {
        it.resourceManager = resourceManager
        it.entryAuditService = entryAuditService
    }

    @Test
    fun testContextConstruct() {
        val url = "youtube.com"
        val type = YoutubeDlTask.YoutubeDlDownload.BEST_AUDIO
        val context = youtubeDlTask.createContext(mapOf("url" to url, "type" to type.toString()))
        assertThat(context.type).isEqualTo(type)
        assertThat(context.url).isEqualTo(url)
    }

    @Test
    fun testBuilder() {
        val url = "youtube.com"
        val type = YoutubeDlTask.YoutubeDlDownload.BEST_AUDIO
        val builder = YoutubeDlTask.build(url, type)
        assertThat(builder.clazz).isEqualTo(YoutubeDlTask::class)
        assertThat(builder.context.input).contains(entry("url", url), entry("type", type.toString()))
    }

    @Test
    fun testProcess() {
        val url = "youtube.com/watch?v=1234"
        val type = YoutubeDlTask.YoutubeDlDownload.BEST_AUDIO
        val context = youtubeDlTask.createContext(mapOf("url" to url, "type" to type.toString()))

        val name = "greatvid.webm"
        val commandResult = """
            first line
            [download] Destination: $name
            after line
            """.trimIndent()

        val path = Paths.get(name)
        every { resourceManager.constructPath("eid", any()) } returns path

        every { resourceManager.saveGeneratedResource(
                entryId = "eid",
                type = ResourceType.GENERATED,
                path = path) } returns
                Resource("rid", "eid", name, "", ResourceType.UPLOAD, 1, 1, 1)

        mockkObject(ExecUtils)

        every { ExecUtils.executeCommand(any()) } returns Result.Success(commandResult)

        runBlocking {
            youtubeDlTask.process(context)
        }

        verify(exactly = 1) { ExecUtils.executeCommand(match {
            it.startsWith("youtube-dl") && it.endsWith(url)
        }) }

        unmockkObject(ExecUtils)

        verify(exactly = 1) {
            resourceManager.saveGeneratedResource(
                    entryId = "eid",
                    type = ResourceType.GENERATED,
                    path = path)
        }

        verify(exactly = 1) { resourceManager.constructPath("eid", any()) }
        verify(exactly = 1) { entryAuditService.acceptAuditEvent("eid", any(), any())  }
    }

    @Test
    fun testProcessBadResult() {
        val url = "youtube.com/watch?v=1234"
        val type = YoutubeDlTask.YoutubeDlDownload.BEST_AUDIO
        val context = youtubeDlTask.createContext(mapOf("url" to url, "type" to type.toString()))

        every { resourceManager.constructPath("eid", any()) } returns Paths.get("file.webm")

        mockkObject(ExecUtils)

        every { ExecUtils.executeCommand(any()) } returns Result.Failure(ExecException(-1, "error"))

        runBlocking {
            youtubeDlTask.process(context)
        }

        unmockkObject(ExecUtils)

        verify(exactly = 1) { resourceManager.constructPath("eid", any()) }
        verify(exactly = 1) { entryAuditService.acceptAuditEvent("eid", any(), any())  }
    }

}