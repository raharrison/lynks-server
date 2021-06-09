package link

import common.BaseProperties
import common.exception.ExecutionException
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import resource.ResourceManager
import resource.ResourceType
import resource.ResourceType.*
import resource.WebResourceRetriever
import util.Result
import java.nio.file.Path

class DefaultLinkProcessorTest {

    private val resourceRetriever = mockk<WebResourceRetriever>()
    private val resourceManager = mockk<ResourceManager>()

    private val scrapeLinkResponse = this.javaClass.getResource("/scrape_link_response.json").readText()
    private val suggestLinkResponse = this.javaClass.getResource("/suggest_link_response.json").readText()
    private val url = "https://ryanharrison.co.uk"

    @Test
    fun testMatches() {
        val processor = DefaultLinkProcessor(url, resourceRetriever, resourceManager)
        assertThat(processor.matches()).isTrue()
    }

    @Test
    fun testEnrichTasks() = runBlocking {
        val processor = DefaultLinkProcessor(url, resourceRetriever, resourceManager)
        val props = BaseProperties()
        processor.enrich(props)
        assertThat(props.tasks).isNotEmpty
        Unit
    }

    @Test
    fun testScrapeResourcesSuccess() = runBlocking {
        every { resourceManager.constructTempBasePath(url) } returns Path.of("tempPath")
        coEvery { resourceRetriever.postStringResult(any(), any()) } returns Result.Success(scrapeLinkResponse)
        val processor = DefaultLinkProcessor(url, resourceRetriever, resourceManager)
        processor.use {
            processor.init()
            val scrapedResources = processor.scrapeResources(ResourceType.linkBaseline())
            assertThat(scrapedResources).hasSize(6)
            assertThat(scrapedResources).extracting("resourceType")
                .containsOnly(SCREENSHOT, PREVIEW, THUMBNAIL, DOCUMENT, PAGE, READABLE_TEXT)
        }
        Unit
    }

    @Test
    fun testScrapeResourcesFailure() = runBlocking {
        every { resourceManager.constructTempBasePath(url) } returns Path.of("tempPath")
        coEvery { resourceRetriever.postStringResult(any(), any()) } returns Result.Failure(ExecutionException("failed"))
        val processor = DefaultLinkProcessor(url, resourceRetriever, resourceManager)
        processor.use {
            processor.init()
            assertThrows<ExecutionException> {
                processor.scrapeResources(ResourceType.linkBaseline())
            }
        }
        Unit
    }

    @Test
    fun testSuggestSuccess() = runBlocking {
        every { resourceManager.constructTempBasePath(url) } returns Path.of("tempPath")
        coEvery { resourceRetriever.postStringResult(any(), any()) } returns Result.Success(suggestLinkResponse)
        val processor = DefaultLinkProcessor(url, resourceRetriever, resourceManager)
        processor.use {
            processor.init()
            val suggestResponse = processor.suggest(ResourceType.linkBaseline())
            assertThat(suggestResponse.resources).hasSize(3)
            assertThat(suggestResponse.resources).extracting("resourceType")
                .containsOnly(PREVIEW, THUMBNAIL, READABLE_TEXT)
            assertThat(suggestResponse.details.url).isNotEmpty()
            assertThat(suggestResponse.details.description).isNotEmpty()
            assertThat(suggestResponse.details.image).isNotEmpty()
            assertThat(suggestResponse.details.title).isEqualTo("Demystifying Java Virtual Machine Memory Management")
            assertThat(suggestResponse.details.keywords).containsExactly("first", "second")
            assertThat(suggestResponse.details.author).isEqualTo("Bob Smith")
        }
        Unit
    }

    @Test
    fun testSuggestFailure() = runBlocking {
        every { resourceManager.constructTempBasePath(url) } returns Path.of("tempPath")
        coEvery { resourceRetriever.postStringResult(any(), any()) } returns Result.Failure(ExecutionException("failed"))
        val processor = DefaultLinkProcessor(url, resourceRetriever, resourceManager)
        processor.use {
            processor.init()
            assertThrows<ExecutionException> {
                processor.suggest(ResourceType.linkBaseline())
            }
        }
        Unit
    }


}
