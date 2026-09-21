package lynks.link

import io.mockk.*
import kotlinx.coroutines.runBlocking
import lynks.common.BaseProperties
import lynks.common.exception.ExecutionException
import lynks.common.exception.SuggestionUnavailableException
import lynks.resource.JPG
import lynks.resource.ResourceManager
import lynks.resource.ResourceType
import lynks.resource.WebResourceRetriever
import lynks.util.Result
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

class YoutubeLinkProcessorTest {

    private val url = "http://youtube.com/watch?v=DAiEUeM8Uv0"
    private val videoId = "DAiEUeM8Uv0"
    private val apiKey = "test-api-key"
    private val retriever = mockk<WebResourceRetriever>()
    private val resourceManager = mockk<ResourceManager>()

    private val vidInfo = this.javaClass.getResource("/get_video_info_v3.json")!!.readText()

    private fun createProcessor(
        testUrl: String = url,
        key: String? = apiKey
    ): YoutubeLinkProcessor = runBlocking {
        YoutubeLinkProcessor(testUrl, retriever, resourceManager, key).apply { init() }
    }

    @Test
    fun testGetAttributes() {
        val processor = createProcessor(key = null)
        processor.use {
            assertThat(processor.url).isEqualTo(url)
        }
    }

    @Test
    fun testMatches() {
        assertThat(YoutubeLinkProcessor(url, retriever, resourceManager).matches()).isTrue()
        assertThat(YoutubeLinkProcessor("http://youtube.com/something", retriever, resourceManager).matches()).isTrue()
        assertThat(YoutubeLinkProcessor("http://www.youtube.com/watch?v=abc", retriever, resourceManager).matches()).isTrue()
        assertThat(YoutubeLinkProcessor("http://m.youtube.com/watch?v=abc", retriever, resourceManager).matches()).isTrue()
        assertThat(YoutubeLinkProcessor("http://music.youtube.com/watch?v=abc", retriever, resourceManager).matches()).isTrue()
        assertThat(YoutubeLinkProcessor("https://youtu.be/DAiEUeM8Uv0", retriever, resourceManager).matches()).isTrue()
        assertThat(YoutubeLinkProcessor("http://youtu.com/watch?v=DAiEUeM8Uv0", retriever, resourceManager).matches()).isFalse()
        assertThat(YoutubeLinkProcessor("http://google.com", retriever, resourceManager).matches()).isFalse()
    }

    @Test
    fun testSuggest() = runBlocking {
        coEvery { retriever.getStringResult(any()) } returns Result.Success(vidInfo)
        val processor = createProcessor()
        val suggestResponse = processor.suggest(EnumSet.noneOf(ResourceType::class.java))
        assertThat(suggestResponse.details.url).isEqualTo(url)
        assertThat(suggestResponse.details.title).isEqualTo("When Your Phone is at 1%")
        assertThat(suggestResponse.details.keywords).hasSizeGreaterThan(5)
        assertThat(suggestResponse.details.author).isEqualTo("Beluga")
        assertThat(suggestResponse.details.published).isEqualTo("2021-10-01T12:00:00Z")
        assertThat(suggestResponse.details.description).isNotEmpty()
        assertThat(suggestResponse.details.image).isEqualTo("https://i.ytimg.com/vi/$videoId/mqdefault.jpg")
        coVerify(exactly = 1) { retriever.getStringResult(any()) }
        Unit
    }

    @Test
    fun testSuggestApiFailure() = runBlocking {
        coEvery { retriever.getStringResult(any()) } returns Result.Failure(ExecutionException("error"))
        val processor = createProcessor()
        assertThatThrownBy { runBlocking { processor.suggest(EnumSet.noneOf(ResourceType::class.java)) } }
            .isInstanceOf(SuggestionUnavailableException::class.java)
            .hasMessage("YouTube suggestions unavailable")
        coVerify(exactly = 1) { retriever.getStringResult(any()) }
        Unit
    }

    @Test
    fun testSuggestNoApiKey() = runBlocking {
        val processor = createProcessor(key = null)
        assertThatThrownBy { runBlocking { processor.suggest(EnumSet.noneOf(ResourceType::class.java)) } }
            .isInstanceOf(SuggestionUnavailableException::class.java)
            .hasMessage("YouTube suggestions not enabled")
        coVerify(exactly = 0) { retriever.getStringResult(any()) }
        Unit
    }

    @Test
    fun testSuggestEmptyItems() = runBlocking {
        coEvery { retriever.getStringResult(any()) } returns Result.Success("""{"items":[]}""")
        val processor = createProcessor()
        assertThatThrownBy { runBlocking { processor.suggest(EnumSet.noneOf(ResourceType::class.java)) } }
            .isInstanceOf(SuggestionUnavailableException::class.java)
            .hasMessage("YouTube suggestions unavailable")
        Unit
    }

    @Test
    fun testSuggestPreviewFallsBackToStandardWhenNoMaxres() = runBlocking {
        val noMaxresJson = """{"items":[{"snippet":{
            "title":"When Your Phone is at 1%",
            "thumbnails":{
                "medium":{"url":"https://i.ytimg.com/vi/$videoId/mqdefault.jpg"},
                "standard":{"url":"https://i.ytimg.com/vi/$videoId/sddefault.jpg"}
            }
        }}]}"""
        coEvery { retriever.getStringResult(any()) } returns Result.Success(noMaxresJson)
        every { resourceManager.saveTempFile(any(), any(), any(), any()) } returns "path"
        coEvery { retriever.getFile(any()) } returns byteArrayOf(1, 2, 3)
        val processor = createProcessor()
        processor.suggest(EnumSet.of(ResourceType.PREVIEW))
        coVerify { retriever.getFile("https://i.ytimg.com/vi/$videoId/sddefault.jpg") }
        Unit
    }

    @Test
    fun testShortsUrl() = runBlocking {
        coEvery { retriever.getStringResult(any()) } returns Result.Success(vidInfo)
        val shortsUrl = "https://www.youtube.com/shorts/$videoId"
        val processor = createProcessor(testUrl = shortsUrl)
        assertThat(processor.matches()).isTrue()
        val suggestResponse = processor.suggest(EnumSet.noneOf(ResourceType::class.java))
        assertThat(suggestResponse.details.title).isEqualTo("When Your Phone is at 1%")
        Unit
    }

    @Test
    fun testEmbedUrl() = runBlocking {
        coEvery { retriever.getStringResult(any()) } returns Result.Success(vidInfo)
        val embedUrl = "https://www.youtube.com/embed/$videoId"
        val processor = createProcessor(testUrl = embedUrl)
        assertThat(processor.matches()).isTrue()
        val suggestResponse = processor.suggest(EnumSet.noneOf(ResourceType::class.java))
        assertThat(suggestResponse.details.title).isEqualTo("When Your Phone is at 1%")
        Unit
    }

    @Test
    fun testYoutuBeUrl() = runBlocking {
        coEvery { retriever.getStringResult(any()) } returns Result.Success(vidInfo)
        val shortUrl = "https://youtu.be/$videoId"
        val processor = createProcessor(testUrl = shortUrl)
        assertThat(processor.matches()).isTrue()
        val suggestResponse = processor.suggest(EnumSet.noneOf(ResourceType::class.java))
        assertThat(suggestResponse.details.title).isEqualTo("When Your Phone is at 1%")
        Unit
    }

    @Test
    fun testGenerateThumbnail() = runBlocking {
        coEvery { retriever.getStringResult(any()) } returns Result.Success(vidInfo)
        val processor = createProcessor()
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
        Unit
    }

    @Test
    fun testGeneratePreview() = runBlocking {
        coEvery { retriever.getStringResult(any()) } returns Result.Success(vidInfo)
        val processor = createProcessor()
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
        Unit
    }

    @Test
    fun testEnrichAttributes() = runBlocking {
        coEvery { retriever.getStringResult(any()) } returns Result.Success(vidInfo)
        val processor = createProcessor()
        val props = BaseProperties()
        processor.enrich(props)
        assertThat(props.attributes).hasSize(1)
            .containsExactly(entry("embedUrl", "https://www.youtube.com/embed/$videoId"))
        Unit
    }

    @Test
    fun testEnrichTasks() = runBlocking {
        coEvery { retriever.getStringResult(any()) } returns Result.Success(vidInfo)
        val processor = createProcessor()
        val props = BaseProperties()
        processor.enrich(props)
        assertThat(props.tasks).extracting("description")
            .containsExactlyInAnyOrder("Process Link", "Find Discussions")
        Unit
    }
}
