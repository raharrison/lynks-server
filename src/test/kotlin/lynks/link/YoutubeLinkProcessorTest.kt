package lynks.link

import io.mockk.*
import kotlinx.coroutines.runBlocking
import lynks.common.BaseProperties
import lynks.common.exception.ExecutionException
import lynks.resource.JPG
import lynks.resource.ResourceManager
import lynks.resource.ResourceType
import lynks.resource.WebResourceRetriever
import lynks.task.link.LinkProcessingTask
import lynks.task.youtube.YoutubeDlTask
import lynks.util.Result
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import java.util.*

class YoutubeLinkProcessorTest {

    private val url = "http://youtube.com/watch?v=DAiEUeM8Uv0"
    private val retriever = mockk<WebResourceRetriever>()
    private val resourceManager = mockk<ResourceManager>()
    private val processor = createProcessor()

    @Test
    fun testGetAttributes() {
        processor.use {
            assertThat(processor.url).isEqualTo(url)
        }
    }

    @Test
    fun testSuggest() = runBlocking {
        val vidInfo = this.javaClass.getResource("/get_video_info.txt").readText()
        coEvery { retriever.postStringResult(any(), any()) } returns Result.Success(vidInfo)
        val suggestResponse = processor.suggest(EnumSet.noneOf(ResourceType::class.java))
        assertThat(suggestResponse.details.url).isEqualTo(url)
        assertThat(suggestResponse.details.keywords).hasSizeGreaterThan(5)
        assertThat(suggestResponse.details.title).isEqualTo("When Your Phone is at 1%")
        coVerify(exactly = 1) { retriever.postStringResult(any(), any()) }
    }

    @Test
    fun testGetTitleBadInfo() = runBlocking {
        coEvery { retriever.postStringResult(any(), any()) } returns Result.Failure(ExecutionException("error"))
        val suggestResponse = processor.suggest(EnumSet.noneOf(ResourceType::class.java))
        assertThat(suggestResponse.details.title).isEmpty()
    }

    @Test
    fun testMatches() {
        assertThat(YoutubeLinkProcessor(url, retriever, resourceManager).matches()).isTrue()
        assertThat(YoutubeLinkProcessor("http://youtube.com/something", retriever, resourceManager).matches()).isTrue()
        assertThat(YoutubeLinkProcessor("http://youtu.com/watch?v=DAiEUeM8Uv0", retriever, resourceManager).matches()).isFalse()
        assertThat(YoutubeLinkProcessor("http://google.com", retriever, resourceManager).matches()).isFalse()
    }

    @Test
    fun testGenerateThumbnail() = runBlocking {
        val img = byteArrayOf(1, 2, 3, 4, 5)
        every { resourceManager.saveTempFile(url, img, ResourceType.THUMBNAIL, JPG) } returns "thumbPath"
        coEvery { retriever.getFile(any()) } returns img
        val resourceSet = EnumSet.of(ResourceType.THUMBNAIL)
        val processedResources = processor.scrapeResources(resourceSet)
        assertThat(processedResources).hasSize(1)
        val thumb = processedResources.find { it.resourceType == ResourceType.THUMBNAIL }
        assertThat(thumb?.extension).isEqualTo("jpg")
        assertThat(thumb?.targetPath).isEqualTo("thumbPath")
        verify(exactly = 1) { resourceManager.saveTempFile(url, img, ResourceType.THUMBNAIL, JPG) }
    }

    @Test
    fun testGeneratePreview() = runBlocking {
        val img = byteArrayOf(5, 6, 7, 8, 9)
        every { resourceManager.saveTempFile(url, img, ResourceType.PREVIEW, JPG) } returns "previewPath"
        coEvery { retriever.getFile(any()) } returns img
        val resourceSet = EnumSet.of(ResourceType.PREVIEW)
        val processedResources = processor.scrapeResources(resourceSet)
        assertThat(processedResources).hasSize(1)
        val preview = processedResources.find { it.resourceType == ResourceType.PREVIEW }
        assertThat(preview?.extension).isEqualTo("jpg")
        assertThat(preview?.targetPath).isEqualTo("previewPath")
        verify(exactly = 1) { resourceManager.saveTempFile(url, img, ResourceType.PREVIEW, JPG) }
    }

    @Test
    fun testEnrichAttributes() = runBlocking {
        val props = BaseProperties()
        processor.enrich(props)
        assertThat(props.attributes).hasSize(1)
            .containsExactly(entry("embedUrl", "https://www.youtube.com/embed/DAiEUeM8Uv0"))
        Unit
    }

    @Test
    fun testEnrichTasks() = runBlocking {
        val props = BaseProperties()
        processor.enrich(props)
        assertThat(props.tasks).extracting("description")
            .contains("Process Link", "Download Audio", "Download Video (max 720p)", "Download Video (max 1080p)")
        assertThat(props.tasks).extracting("className")
            .contains(LinkProcessingTask::class.qualifiedName, YoutubeDlTask::class.qualifiedName)
        val types = props.tasks.mapNotNull {
            it.input["type"]
        }
        assertThat(types).hasSize(3).containsAll(YoutubeDlTask.YoutubeDlDownload.values().map { it.toString() })
        Unit
    }

    private fun createProcessor(): YoutubeLinkProcessor = runBlocking {
        YoutubeLinkProcessor(url, retriever, resourceManager).apply { init() }
    }

}
