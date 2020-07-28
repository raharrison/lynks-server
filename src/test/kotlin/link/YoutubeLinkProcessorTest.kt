package link

import common.BaseProperties
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import link.extract.ExtractionPolicy
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import resource.ResourceRetriever
import resource.ResourceType
import task.LinkProcessingTask
import task.YoutubeDlTask
import java.util.*

class YoutubeLinkProcessorTest {

    private val url = "http://youtube.com/watch?v=DAiEUeM8Uv0"
    private val retriever = mockk<ResourceRetriever>()
    private val processor = createProcessor()

    @Test
    fun testGetAttributes() = runBlocking {
        processor.use {
            assertThat(processor.resolvedUrl).isEqualTo(url)
            Unit
        }
    }

    @Test
    fun testGetTitle() = runBlocking {
        val vidInfo = this.javaClass.getResource("/get_video_info.txt").readText()
        coEvery { retriever.getString(any()) } returns vidInfo
        assertThat(processor.linkContent.title).isEqualTo("Savoy - How U Like Me Now (feat. Roniit) [Monstercat Release]")
        coVerify(exactly = 1) { retriever.getString(any()) }
    }

    @Test
    fun testGetKeywords() = runBlocking {
        val vidInfo = this.javaClass.getResource("/get_video_info.txt").readText()
        coEvery { retriever.getString(any()) } returns vidInfo
        assertThat(processor.linkContent.keywords).hasSizeGreaterThan(5)
        coVerify(exactly = 1) { retriever.getString(any()) }
    }

    @Test
    fun testGetTitleBadInfo() = runBlocking {
        coEvery { retriever.getString(any()) } returns null
        assertThat(processor.linkContent.title).isEmpty()
    }

    @Test
    fun testMatches() {
        assertThat(YoutubeLinkProcessor(ExtractionPolicy.FULL, url, retriever).matches()).isTrue()
        assertThat(YoutubeLinkProcessor(ExtractionPolicy.FULL, "http://youtube.com/something", retriever).matches()).isTrue()
        assertThat(YoutubeLinkProcessor(ExtractionPolicy.FULL, "http://youtu.com/watch?v=DAiEUeM8Uv0", retriever).matches()).isFalse()
        assertThat(YoutubeLinkProcessor(ExtractionPolicy.FULL, "http://google.com", retriever).matches()).isFalse()
    }

    @Test
    fun testGenerateThumbnail() = runBlocking {
        val img = byteArrayOf(1, 2, 3, 4, 5)
        coEvery { retriever.getFile(any()) } returns img
        val resourceSet = EnumSet.of(ResourceType.THUMBNAIL)
        val processedResources = processor.process(resourceSet)
        assertThat(processedResources).hasSize(1)
        val thumb = processedResources[ResourceType.THUMBNAIL] as GeneratedImageResource
        assertThat(thumb.extension).isEqualTo("jpg")
        assertThat(thumb.image).isEqualTo(img)
        Unit
    }

    @Test
    fun testGeneratePreview() = runBlocking {
        val img = byteArrayOf(5, 6, 7, 8, 9)
        coEvery { retriever.getFile(any()) } returns img
        val resourceSet = EnumSet.of(ResourceType.PREVIEW)
        val processedResources = processor.process(resourceSet)
        assertThat(processedResources).hasSize(1)
        val preview = processedResources[ResourceType.PREVIEW] as GeneratedImageResource
        assertThat(preview.extension).isEqualTo("jpg")
        assertThat(preview.image).isEqualTo(img)
        Unit
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
        YoutubeLinkProcessor(ExtractionPolicy.FULL, url, retriever).apply { init() }
    }

}
