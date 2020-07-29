package link

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import link.extract.ExtractionPolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import resource.HTML
import resource.PNG
import resource.ResourceType
import resource.WebResourceRetriever
import java.util.*

class DefaultLinkProcessorTest {

    private val resourceRetriever = mockk<WebResourceRetriever>()

    private val rawHtml = this.javaClass.getResource("/content_extract_raw.html").readText()
    private val image = this.javaClass.getResource("/test_image.png").readBytes()
    private val url = "https://ryanharrison.co.uk"

    @Test
    fun testDefaultLinkProcessorPartialExtraction() = runBlocking {
        val processor = DefaultLinkProcessor(ExtractionPolicy.PARTIAL, url, resourceRetriever)
        coEvery { resourceRetriever.getString(url) } returns rawHtml
        coEvery { resourceRetriever.getFile(any()) } returns image

        processor.use {
            it.init()
            val linkContent = it.linkContent
            assertThat(linkContent.title).isEqualTo("Kotlin & Java CI with Github Actions - Ryan Harrison")
            assertThat(linkContent.rawContent).isNotBlank()
            assertThat(linkContent.extractedContent).isNotBlank()
            assertThat(linkContent.keywords).contains("kotlin", "github", "actions", "build")
            assertThat(it.resolvedUrl).isEqualTo(url)
            assertThat(it.matches()).isTrue()

            val resourceTypes =
                EnumSet.of(ResourceType.THUMBNAIL, ResourceType.PREVIEW, ResourceType.READABLE, ResourceType.PAGE)
            val generatedResources = it.process(resourceTypes)
            assertThat(generatedResources).hasSize(4)

            val screen = generatedResources[ResourceType.PREVIEW] as GeneratedImageResource
            assertThat(screen.extension).isEqualTo(PNG)
            assertThat(screen.image).isNotEmpty()

            val thumb = generatedResources[ResourceType.THUMBNAIL] as GeneratedImageResource
            assertThat(thumb.extension).isEqualTo(PNG)
            assertThat(thumb.image).isNotEmpty()

            val readable = generatedResources[ResourceType.READABLE] as GeneratedDocResource
            assertThat(readable.extension).isEqualTo(HTML)
            assertThat(readable.doc).isNotEmpty()

            val page = generatedResources[ResourceType.PAGE] as GeneratedDocResource
            assertThat(page.extension).isEqualTo(HTML)
            assertThat(page.doc).isEqualTo(rawHtml)
        }
        Unit
    }

    @Test
    fun testDefaultLinkProcessorPartialExtractScreenshotOrDocThrows() = runBlocking {
        val processor = DefaultLinkProcessor(ExtractionPolicy.PARTIAL, url, resourceRetriever)

        processor.use {
            assertThrows<Exception> {
                runBlocking {
                    it.process(EnumSet.of(ResourceType.SCREENSHOT))
                }
            }
            assertThrows<Exception> {
                runBlocking {
                    it.process(EnumSet.of(ResourceType.DOCUMENT))
                }
            }
        }
        Unit
    }

    @Test
    fun testImageResourceEquality() {
        val img1 = GeneratedImageResource(byteArrayOf(1, 2, 3, 4, 5), "JPG")
        val img2 = GeneratedImageResource(byteArrayOf(1, 2, 3, 4, 5), "JPG")
        assertThat(img1).isNotEqualTo("")
        assertThat(img1).isEqualTo(img2)
        assertThat(img1.hashCode()).isEqualTo(img2.hashCode())

        val img3 = GeneratedImageResource(byteArrayOf(1, 2, 3, 4), "JPG")
        assertThat(img1).isNotEqualTo(img3)
        assertThat(img1.hashCode()).isNotEqualTo(img3.hashCode())

        val img4 = GeneratedImageResource(byteArrayOf(1, 2, 3, 4), "PNG")
        assertThat(img4).isNotEqualTo(img3)
        assertThat(img4.hashCode()).isNotEqualTo(img3.hashCode())
    }

}
