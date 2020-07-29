package link

import common.ServerTest
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import link.extract.ExtractionPolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import resource.*

class DefaultLinkProcessorIntegrationTest: ServerTest() {

    private val resourceRetriever = mockk<WebResourceRetriever>()
    private val processorFactory = LinkProcessorFactory(resourceRetriever)

    @Test
    fun testDefaultLinkProcessorFullExtraction() = runBlocking {
        val url = "https://ryanharrison.co.uk"
        val processor = DefaultLinkProcessor(ExtractionPolicy.FULL, url, resourceRetriever)
        processor.use {
            it.init()
            val linkContent = it.linkContent
            assertThat(linkContent.title).isEqualTo("My blog, portfolio and technology related ramblings")
            assertThat(linkContent.rawContent).isNotBlank()
            assertThat(linkContent.extractedContent).isNotBlank()
            assertThat(linkContent.keywords).contains("technology", "programming", "java", "blog", "code", "software")
            assertThat(it.resolvedUrl).isEqualTo("$url/")
            assertThat(it.matches()).isTrue()

            val generatedResources = it.process(ResourceType.all())
            assertThat(generatedResources).hasSize(6)

            val screen = generatedResources[ResourceType.SCREENSHOT] as GeneratedImageResource
            assertThat(screen.extension).isEqualTo(PNG)
            assertThat(screen.image).isNotEmpty()

            val thumb = generatedResources[ResourceType.THUMBNAIL] as GeneratedImageResource
            assertThat(thumb.extension).isEqualTo(JPG)
            assertThat(thumb.image).isNotEmpty()

            val preview = generatedResources[ResourceType.PREVIEW] as GeneratedImageResource
            assertThat(preview.extension).isEqualTo(JPG)
            assertThat(preview.image).isNotEmpty()

            val document = generatedResources[ResourceType.DOCUMENT] as GeneratedImageResource
            assertThat(document.extension).isEqualTo(PDF)
            assertThat(document.image).isNotEmpty()

            val readable = generatedResources[ResourceType.READABLE] as GeneratedDocResource
            assertThat(readable.extension).isEqualTo(HTML)
            assertThat(readable.doc).isNotEmpty()

            val page = generatedResources[ResourceType.PAGE] as GeneratedDocResource
            assertThat(page.extension).isEqualTo(HTML)
            assertThat(page.doc).isNotEmpty()
        }
        Unit
    }

    @Test
    fun testDefaultLinkProcessorInvalidUrl() {
        assertThrows<Exception> {
            runBlocking {
                processorFactory.createProcessors("invalid.invalid", ExtractionPolicy.FULL).forEach {
                    it.init()
                }
            }
        }
        // 404 error
        assertThrows<Exception> {
            runBlocking {
                processorFactory.createProcessors("https://ryanharrison.co.uk/nothinghere", ExtractionPolicy.FULL)
                    .forEach {
                        it.init()
                    }
            }
        }
    }


}
