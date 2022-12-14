package lynks.task.youtube

import io.mockk.*
import kotlinx.coroutines.runBlocking
import lynks.common.Environment
import lynks.common.Link
import lynks.common.exception.ExecutionException
import lynks.entry.EntryAuditService
import lynks.notify.Notification
import lynks.notify.NotificationType
import lynks.notify.NotifyService
import lynks.resource.*
import lynks.util.ExecUtils
import lynks.util.FileUtils
import lynks.util.Result
import org.apache.commons.lang3.SystemUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Paths

class YoutubeDlTaskTest {

    private val resourceRetriever = mockk<WebResourceRetriever>()
    private val resourceManager = mockk<ResourceManager>()
    private val entryAuditService = mockk<EntryAuditService>(relaxUnitFun = true)
    private val notifyService = mockk<NotifyService>()

    private val youtubeDlRunner = YoutubeDlRunner(resourceRetriever, resourceManager, entryAuditService, notifyService)

    private val link = Link("eid", "title", "youtube.com/watch?v=1234", "src", "", 123L, 123L)

    @BeforeEach
    fun setup() {
        coEvery { notifyService.create(any()) } returns Notification(
            "n1", NotificationType.PROCESSED, "completed", false, dateCreated = System.currentTimeMillis()
        )
    }

    @AfterEach
    fun cleanup() {
        FileUtils.deleteDirectories(listOf(Paths.get(Environment.resource.binaryBasePath)))
    }

    @Test
    fun testProcessDownloadYoutubeDl() {
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
            Resource("rid", "pid", "eid", 1, name, "", ResourceType.UPLOAD, 1, 1)

        every { resourceManager.migrateGeneratedResources("eid", any()) } returns emptyList()
        mockkObject(ExecUtils)

        every { ExecUtils.executeCommand(any()) } returns Result.Success(commandResult)

        runBlocking {
            youtubeDlRunner.run(link.id, link.url, "format")
        }

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
        coVerify { notifyService.create(any()) }
        verify(exactly = 1) { entryAuditService.acceptAuditEvent("eid", any(), any()) }
    }

    @Test
    fun testProcessBadResult() {
        val binaryName = "yt-dlp${if (SystemUtils.IS_OS_WINDOWS) ".exe" else ""}"
        val binaryPath = Paths.get(Environment.resource.binaryBasePath, binaryName)
        FileUtils.writeToFile(binaryPath, byteArrayOf(1, 2, 3))

        every { resourceManager.constructTempBasePath("eid") } returns Paths.get("file.webm")

        mockkObject(ExecUtils)

        every { ExecUtils.executeCommand(any()) } returns Result.Failure(ExecutionException("error"))

        runBlocking {
            youtubeDlRunner.run(link.id, link.url, "format")
        }

        unmockkObject(ExecUtils)

        coVerify(exactly = 0) { resourceRetriever.getFileResult(any()) }
        verify(exactly = 1) { resourceManager.constructTempBasePath("eid") }
        verify(exactly = 0) { resourceManager.migrateGeneratedResources("eid", any()) }
        coVerify { notifyService.create(any()) }
        verify(exactly = 1) { entryAuditService.acceptAuditEvent("eid", any(), any()) }
    }

    @Test
    fun testProcessNoReturnedFileName() {
        coEvery { resourceRetriever.getFileResult(any()) } returns Result.Success(byteArrayOf(1, 2, 3))
        every { resourceManager.constructTempBasePath("eid") } returns Paths.get("video.webm")
        mockkObject(ExecUtils)

        every { ExecUtils.executeCommand(any()) } returns Result.Success("invalid")

        runBlocking {
            youtubeDlRunner.run(link.id, link.url, "format")
        }

        unmockkObject(ExecUtils)

        verify(exactly = 0) { resourceManager.migrateGeneratedResources("eid", any()) }
    }

    @Test
    fun testDownloadYoutubeDlFailed() {
        val exception = ExecutionException("failed")
        coEvery { resourceRetriever.getFileResult(any()) } returns Result.Failure(exception)

        runBlocking {
            assertThrows<ExecutionException> {
                youtubeDlRunner.run(link.id, link.url, "format")
            }
        }
    }

    @Test
    fun testInvalidContextUrl() {
        runBlocking {
            assertThrows<IllegalArgumentException> {
                youtubeDlRunner.run(link.id, "bad input", "format")
            }
        }

    }
}
