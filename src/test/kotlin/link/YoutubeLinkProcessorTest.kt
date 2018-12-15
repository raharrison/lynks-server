package link

import common.BaseProperties
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import resource.ResourceRetriever
import task.LinkProcessingTask
import task.YoutubeDlTask

class YoutubeLinkProcessorTest {

    private val url = "http://youtube.com/watch?v=DAiEUeM8Uv0"
    private val retriever = mockk<ResourceRetriever>()
    private val processor = createProcessor()

    @Test
    fun testGetAttributes() = runBlocking {
        processor.use {
            assertThat(processor.html).isNull()
            assertThat(processor.content).isNull()
            assertThat(processor.resolvedUrl).isEqualTo(url)
            assertThat(processor.printPage()).isNull()
        }
    }

    @Test
    fun testGetTitle() {
        val vidInfo = this.javaClass.getResource("/get_video_info.txt").readText()
        coEvery { retriever.getString(any()) } returns vidInfo
        assertThat(processor.title).isEqualTo("Savoy - How U Like Me Now (feat. Roniit) [Monstercat Release]")
        coVerify(exactly = 1) { retriever.getString(any()) }
    }

    @Test
    fun testGetTitleBadInfo() {
        coEvery { retriever.getString(any()) } returns null
        assertThat(processor.title).isEmpty()
    }

    @Test
    fun testMatches() {
        assertThat(processor.matches(url)).isTrue()
        assertThat(processor.matches("http://youtube.com/something")).isTrue()
        assertThat(processor.matches("http://youtu.com/watch?v=DAiEUeM8Uv0")).isFalse()
        assertThat(processor.matches("http://google.com")).isFalse()
    }

    @Test
    fun testGenerateThumbnail() = runBlocking {
        val img = byteArrayOf(1,2,3,4,5)
        coEvery { retriever.getFile(any()) } returns img
        val thumb = processor.generateThumbnail()
        assertThat(thumb).isNotNull
        assertThat(thumb?.extension).isEqualTo("jpg")
        assertThat(thumb?.image).isEqualTo(img)
        Unit
    }

    @Test
    fun testGenerateScreenshot() = runBlocking {
        val img = byteArrayOf(5,6,7,8,9)
        coEvery { retriever.getFile(any()) } returns img
        val thumb = processor.generateScreenshot()
        assertThat(thumb).isNotNull
        assertThat(thumb?.extension).isEqualTo("jpg")
        assertThat(thumb?.image).isEqualTo(img)
        Unit
    }

    @Test
    fun testEnrichAttributes() = runBlocking {
        val props = BaseProperties()
        processor.enrich(props)
        assertThat(props.attributes).hasSize(1).containsExactly(entry("embedUrl", "https://www.youtube.com/embed/DAiEUeM8Uv0"))
        Unit
    }

    @Test
    fun testEnrichTasks() = runBlocking {
        val props = BaseProperties()
        processor.enrich(props)
        assertThat(props.tasks).extracting("description").contains("Process Link", "Download Audio", "Download Video (max 720p)", "Download Video (max 1080p)")
        assertThat(props.tasks).extracting("className").contains(LinkProcessingTask::class.qualifiedName, YoutubeDlTask::class.qualifiedName)
        val types = props.tasks.mapNotNull {
            it.input["type"]
        }
        assertThat(types).hasSize(3).containsAll(YoutubeDlTask.YoutubeDlDownload.values().map { it.toString() })
        Unit
    }

    private fun createProcessor(): YoutubeLinkProcessor = runBlocking {
        YoutubeLinkProcessor(retriever).apply { this.init(url) }
    }

}