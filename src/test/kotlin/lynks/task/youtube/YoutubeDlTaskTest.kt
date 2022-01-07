package lynks.task.youtube

import io.mockk.*
import kotlinx.coroutines.runBlocking
import lynks.common.Environment
import lynks.common.Link
import lynks.common.exception.ExecutionException
import lynks.entry.EntryAuditService
import lynks.entry.LinkService
import lynks.resource.*
import lynks.util.ExecUtils
import lynks.util.FileUtils
import lynks.util.Result
import org.apache.commons.lang3.SystemUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Paths

class YoutubeDlTaskTest {

    private val linkService = mockk<LinkService>()
    private val resourceManager = mockk<ResourceManager>()
    private val resourceRetriever = mockk<WebResourceRetriever>()
    private val entryAuditService = mockk<EntryAuditService>(relaxUnitFun = true)

    private val youtubeDlTask = YoutubeDlTask("tid", "eid").also {
        it.linkService = linkService
        it.resourceManager = resourceManager
        it.resourceRetriever = resourceRetriever
        it.entryAuditService = entryAuditService
    }

    private val link = Link("eid", "title", "youtube.com/watch?v=1234", "src", "", 123L, 123L)

    @AfterEach
    fun setup() {
        FileUtils.deleteDirectories(listOf(Paths.get(Environment.resource.binaryBasePath)))
    }

    @Test
    fun testContextConstruct() {
        val type = YoutubeDlTask.YoutubeDlDownload.BEST_AUDIO
        val context = youtubeDlTask.createContext(mapOf("type" to type.toString()))
        assertThat(context.type).isEqualTo(type)
    }

    @Test
    fun testBuilder() {
        val builder = YoutubeDlTask.build()
        assertThat(builder.clazz).isEqualTo(YoutubeDlTask::class)
        assertThat(builder.params).isNotEmpty()
    }

    @Test
    fun testProcessDownloadYoutubeDl() {
        val type = YoutubeDlTask.YoutubeDlDownload.BEST_AUDIO
        val context = youtubeDlTask.createContext(mapOf("type" to type.name))

        every { linkService.get(link.id) } returns link
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

        coVerify(exactly = 1) { linkService.get("eid") }
        coVerify(exactly = 1) { resourceRetriever.getFileResult(any()) }

        verify(exactly = 1) {
            ExecUtils.executeCommand(match {
                it.contains(Paths.get(Environment.resource.binaryBasePath, "yt-dlp").toString()) && it.endsWith(link.url)
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
        val type = YoutubeDlTask.YoutubeDlDownload.BEST_VIDEO
        val context = youtubeDlTask.createContext(mapOf("type" to type.name))

        every { linkService.get(link.id) } returns link

        val binaryName = "yt-dlp${if (SystemUtils.IS_OS_WINDOWS) ".exe" else ""}"
        val binaryPath = Paths.get(Environment.resource.binaryBasePath, binaryName)
        FileUtils.writeToFile(binaryPath, byteArrayOf(1, 2, 3))

        every { resourceManager.constructTempBasePath("eid") } returns Paths.get("file.webm")

        mockkObject(ExecUtils)

        every { ExecUtils.executeCommand(any()) } returns Result.Failure(ExecutionException("error"))

        runBlocking {
            youtubeDlTask.process(context)
        }

        unmockkObject(ExecUtils)

        coVerify(exactly = 1) { linkService.get("eid") }
        coVerify(exactly = 0) { resourceRetriever.getFileResult(any()) }
        verify(exactly = 1) { resourceManager.constructTempBasePath("eid") }
        verify(exactly = 0) { resourceManager.migrateGeneratedResources("eid", any()) }
        verify(exactly = 1) { entryAuditService.acceptAuditEvent("eid", any(), any()) }
    }

    @Test
    fun testProcessNoReturnedFileName() {
        val type = YoutubeDlTask.YoutubeDlDownload.BEST_VIDEO_TRANSCODE
        val context = youtubeDlTask.createContext(mapOf("type" to type.name))

        every { linkService.get(link.id) } returns link

        coEvery { resourceRetriever.getFileResult(any()) } returns Result.Success(byteArrayOf(1, 2, 3))
        every { resourceManager.constructTempBasePath("eid") } returns Paths.get("video.webm")
        mockkObject(ExecUtils)

        every { ExecUtils.executeCommand(any()) } returns Result.Success("invalid")

        runBlocking {
            youtubeDlTask.process(context)
        }

        unmockkObject(ExecUtils)

        verify(exactly = 1) { linkService.get("eid") }
        verify(exactly = 0) { resourceManager.migrateGeneratedResources("eid", any()) }
    }

    @Test
    fun testDownloadYoutubeDlFailed() {
        val type = YoutubeDlTask.YoutubeDlDownload.BEST_VIDEO_TRANSCODE
        val context = youtubeDlTask.createContext(mapOf("type" to type.name))

        val exception = ExecutionException("failed")

        coEvery { linkService.get(link.id) } returns link
        coEvery { resourceRetriever.getFileResult(any()) } returns Result.Failure(exception)

        runBlocking {
            assertThrows<ExecutionException> {
                youtubeDlTask.process(context)
            }
        }
    }

    @Test
    fun testInvalidContextUrl() {
        val type = YoutubeDlTask.YoutubeDlDownload.BEST_AUDIO
        val context = youtubeDlTask.createContext(mapOf("type" to type.name))

        every { linkService.get(link.id) } returns link.copy(url = "bad input")

        runBlocking {
            assertThrows<IllegalArgumentException> {
                youtubeDlTask.process(context)
            }
        }

    }
}
