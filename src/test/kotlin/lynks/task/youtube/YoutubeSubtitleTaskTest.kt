package lynks.task.youtube

import io.mockk.*
import kotlinx.coroutines.runBlocking
import lynks.common.Environment
import lynks.common.Link
import lynks.common.exception.ExecutionException
import lynks.entry.EntryAuditService
import lynks.entry.LinkService
import lynks.resource.Resource
import lynks.resource.ResourceManager
import lynks.resource.ResourceType
import lynks.resource.WebResourceRetriever
import lynks.util.ExecUtils
import lynks.util.FileUtils
import lynks.util.Result
import org.apache.commons.lang3.SystemUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlin.io.path.name

class YoutubeSubtitleTaskTest {

    private val linkService = mockk<LinkService>()
    private val resourceManager = mockk<ResourceManager>()
    private val resourceRetriever = mockk<WebResourceRetriever>()
    private val entryAuditService = mockk<EntryAuditService>(relaxUnitFun = true)

    private val youtubeSubtitleTask = YoutubeSubtitleTask("tid", "eid").also {
        it.linkService = linkService
        it.resourceManager = resourceManager
        it.resourceRetriever = resourceRetriever
        it.entryAuditService = entryAuditService
    }
    private val context = youtubeSubtitleTask.createContext(mapOf("searchable" to "true"))
    private val link = Link("eid", "title", "youtube.com/watch?v=1234", "src", "", 123L, 123L)

    @AfterEach
    fun setup() {
        FileUtils.deleteDirectories(listOf(Paths.get(Environment.resource.binaryBasePath)))
    }

    @Test
    fun testContextConstruct() {
        val context = youtubeSubtitleTask.createContext(emptyMap())
        assertThat(context.searchable).isTrue()
        val context2 = youtubeSubtitleTask.createContext(mapOf("searchable" to "true"))
        assertThat(context2.searchable).isTrue()
        val context3 = youtubeSubtitleTask.createContext(mapOf("searchable" to "false"))
        assertThat(context3.searchable).isFalse()
        val context4 = youtubeSubtitleTask.createContext(mapOf("searchable" to "invalid"))
        assertThat(context4.searchable).isFalse()
    }

    @Test
    fun testBuilder() {
        val builder = YoutubeSubtitleTask.build()
        assertThat(builder.clazz).isEqualTo(YoutubeSubtitleTask::class)
        assertThat(builder.params).extracting("name").containsOnly("searchable")
    }

    @Test
    fun testProcessDownloadYoutubeSubtitles() {
        every { linkService.get(link.id) } returns link
        every { linkService.updateSearchableContent(link.id, any()) } returns "updated content"
        coEvery { resourceRetriever.getFileResult(any()) } returns Result.Success(byteArrayOf(1, 2, 3))

        val subtitleFile = Path.of(this.javaClass.getResource("/subtitles.en.ttml").toURI())
        val commandResult = """
            first line
            [info] Writing video subtitles to: ${subtitleFile.absolutePathString()}
            after line
            """.trimIndent()

        val path = Paths.get(link.id)
        every { resourceManager.constructTempBasePath("eid") } returns path

        every {
            resourceManager.saveGeneratedResource(
                entryId = link.id,
                name = subtitleFile.name,
                ResourceType.GENERATED,
                any()
            )
        } returns
            Resource("rid", "pid", link.id, 1, subtitleFile.name, subtitleFile.extension, ResourceType.GENERATED, 1, 1)

        mockkObject(ExecUtils)

        every { ExecUtils.executeCommand(any()) } returns Result.Success(commandResult)

        runBlocking {
            youtubeSubtitleTask.process(context)
        }

        coVerify(exactly = 1) { resourceRetriever.getFileResult(any()) }

        verify(exactly = 1) {
            ExecUtils.executeCommand(match {
                it.contains(
                    Paths.get(Environment.resource.binaryBasePath, "yt-dlp").toString()
                ) && it.endsWith(link.url)
            })
        }

        unmockkObject(ExecUtils)

        verify(exactly = 1) { linkService.get(link.id) }
        verify(exactly = 1) { linkService.updateSearchableContent(link.id, any()) }
        verify(exactly = 1) {
            resourceManager.saveGeneratedResource(
                link.id,
                subtitleFile.name,
                ResourceType.GENERATED,
                any()
            )
        }
        verify(exactly = 1) { resourceManager.constructTempBasePath(link.id) }
        verify(exactly = 1) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
    }

    @Test
    fun testProcessBadResult() {
        val binaryName = "yt-dlp${if (SystemUtils.IS_OS_WINDOWS) ".exe" else ""}"
        val binaryPath = Paths.get(Environment.resource.binaryBasePath, binaryName)
        FileUtils.writeToFile(binaryPath, byteArrayOf(1, 2, 3))

        every { linkService.get(link.id) } returns link
        every { resourceManager.constructTempBasePath(link.id) } returns Paths.get(link.id)

        mockkObject(ExecUtils)

        every { ExecUtils.executeCommand(any()) } returns Result.Failure(ExecutionException("error"))

        runBlocking {
            youtubeSubtitleTask.process(context)
        }

        unmockkObject(ExecUtils)

        coVerify(exactly = 0) { resourceRetriever.getFileResult(any()) }
        verify(exactly = 1) { resourceManager.constructTempBasePath(link.id) }
        verify(exactly = 0) { resourceManager.saveGeneratedResource(link.id, any(), ResourceType.GENERATED, any()) }
        verify(exactly = 1) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
    }

    @Test
    fun testProcessNoReturnedFileName() {
        every { linkService.get(link.id) } returns link
        coEvery { resourceRetriever.getFileResult(any()) } returns Result.Success(byteArrayOf(1, 2, 3))
        every { resourceManager.constructTempBasePath(link.id) } returns Paths.get(link.id)
        mockkObject(ExecUtils)

        every { ExecUtils.executeCommand(any()) } returns Result.Success("invalid")

        runBlocking {
            youtubeSubtitleTask.process(context)
        }

        unmockkObject(ExecUtils)

        verify(exactly = 0) { resourceManager.saveGeneratedResource(link.id, any(), ResourceType.GENERATED, any()) }
    }

    @Test
    fun testDownloadYoutubeDlFailed() {
        every { linkService.get(link.id) } returns link
        coEvery { resourceRetriever.getFileResult(any()) } returns Result.Failure(ExecutionException("failed"))

        runBlocking {
            assertThrows<ExecutionException> {
                youtubeSubtitleTask.process(context)
            }
        }
    }

    @Test
    fun testInvalidContextUrl() {
        every { linkService.get(link.id) } returns link.copy(url = "bad input")

        runBlocking {
            assertThrows<IllegalArgumentException> {
                youtubeSubtitleTask.process(context)
            }
        }
    }


}
