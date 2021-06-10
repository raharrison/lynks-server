package task

import common.Environment
import common.exception.ExecutionException
import entry.EntryAuditService
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.apache.commons.lang3.SystemUtils
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import resource.*
import util.ExecUtils
import util.FileUtils
import util.Result
import java.nio.file.Paths

class YoutubeDlTaskTest {

    private val resourceManager = mockk<ResourceManager>()
    private val resourceRetriever = mockk<ResourceRetriever>()
    private val entryAuditService = mockk<EntryAuditService>(relaxUnitFun = true)

    private val youtubeDlTask = YoutubeDlTask("tid", "eid").also {
        it.resourceManager = resourceManager
        it.resourceRetriever = resourceRetriever
        it.entryAuditService = entryAuditService
    }

    @AfterEach
    fun setup() {
        FileUtils.deleteDirectories(listOf(Paths.get(Environment.resource.binaryBasePath)))
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
    fun testProcessDownloadYoutubeDl() {
        val url = "youtube.com/watch?v=1234"
        val type = YoutubeDlTask.YoutubeDlDownload.BEST_AUDIO
        val context = youtubeDlTask.createContext(mapOf("url" to url, "type" to type.toString()))

        coEvery { resourceRetriever.getFileResult(any()) } returns Result.Success(byteArrayOf(1, 2, 3))

        val name = "greatvid.webm"
        val commandResult = """
            first line
            [download] Destination: $name
            after line
            """.trimIndent()

        val path = Paths.get(name)
        every { resourceManager.constructTempBasePath("eid") } returns path

        every {
            resourceManager.saveGeneratedResource(
                entryId = "eid",
                type = ResourceType.GENERATED,
                path = path
            )
        } returns
            Resource("rid", "eid", name, "", ResourceType.UPLOAD, 1, 1, 1)

        every { resourceManager.migrateGeneratedResources("eid", any()) } returns emptyList()
        mockkObject(ExecUtils)

        every { ExecUtils.executeCommand(any()) } returns Result.Success(commandResult)

        runBlocking {
            youtubeDlTask.process(context)
        }

        coVerify(exactly = 1) { resourceRetriever.getFileResult(any()) }

        verify(exactly = 1) {
            ExecUtils.executeCommand(match {
                it.contains(Paths.get(Environment.resource.binaryBasePath, "youtube-dl").toString()) && it.endsWith(url)
            })
        }

        unmockkObject(ExecUtils)

        verify(exactly = 1) { resourceManager.migrateGeneratedResources("eid", match {
            it.size == 1 && it[0] == GeneratedResource(ResourceType.GENERATED, path.toString(), FileUtils.getExtension(path.toString()))
        }) }

        verify(exactly = 1) { resourceManager.constructTempBasePath("eid") }
        verify(exactly = 1) { entryAuditService.acceptAuditEvent("eid", any(), any()) }
    }

    @Test
    fun testProcessBadResult() {
        val url = "youtube.com/watch?v=1234"
        val type = YoutubeDlTask.YoutubeDlDownload.BEST_VIDEO
        val context = youtubeDlTask.createContext(mapOf("url" to url, "type" to type.toString()))

        val binaryName = "youtube-dl${if (SystemUtils.IS_OS_WINDOWS) ".exe" else ""}"
        val binaryPath = Paths.get(Environment.resource.binaryBasePath, binaryName)
        FileUtils.writeToFile(binaryPath, byteArrayOf(1, 2, 3))

        every { resourceManager.constructTempBasePath("eid") } returns Paths.get("file.webm")

        mockkObject(ExecUtils)

        every { ExecUtils.executeCommand(any()) } returns Result.Failure(ExecutionException("error"))

        runBlocking {
            youtubeDlTask.process(context)
        }

        unmockkObject(ExecUtils)

        coVerify(exactly = 0) { resourceRetriever.getFileResult(any()) }
        verify(exactly = 1) { resourceManager.constructTempBasePath("eid") }
        verify(exactly = 0) { resourceManager.migrateGeneratedResources("eid", any()) }
        verify(exactly = 1) { entryAuditService.acceptAuditEvent("eid", any(), any()) }
    }

    @Test
    fun testProcessNoReturnedFileName() {
        val url = "youtube.com/watch?v=1234"
        val type = YoutubeDlTask.YoutubeDlDownload.BEST_VIDEO_TRANSCODE
        val context = youtubeDlTask.createContext(mapOf("url" to url, "type" to type.toString()))

        coEvery { resourceRetriever.getFileResult(any()) } returns Result.Success(byteArrayOf(1, 2, 3))
        every { resourceManager.constructTempBasePath("eid") } returns Paths.get("video.webm")
        mockkObject(ExecUtils)

        every { ExecUtils.executeCommand(any()) } returns Result.Success("invalid")

        runBlocking {
            youtubeDlTask.process(context)
        }

        unmockkObject(ExecUtils)

        verify(exactly = 0) { resourceManager.migrateGeneratedResources("eid", any()) }
    }

    @Test
    fun testDownloadYoutubeDlFailed() {
        val url = "youtube.com/watch?v=1234"
        val type = YoutubeDlTask.YoutubeDlDownload.BEST_VIDEO_TRANSCODE
        val context = youtubeDlTask.createContext(mapOf("url" to url, "type" to type.toString()))
        val exception = ExecutionException("failed")

        coEvery { resourceRetriever.getFileResult(any()) } returns Result.Failure(exception)

        runBlocking {
            assertThrows<ExecutionException> {
                youtubeDlTask.process(context)
            }
        }

    }
}
