package link

import common.ServerTest
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import resource.JPG
import resource.PDF
import resource.PNG
import worker.LinkProcessorFactory

class LinkProcessorTest: ServerTest() {

    private val processorFactory = LinkProcessorFactory()

    @Test
    fun testDefaultLinkProcessor() = runBlocking {
        val url = "https://ryanharrison.co.uk"
        val processors = processorFactory.createProcessors(url)
        assertThat(processors).hasSize(1)
        assertThat(processors).hasOnlyElementsOfType(DefaultLinkProcessor::class.java)

        processors.first().use {
            it.init()
            assertThat(it.title).isEqualTo("Ryan Harrison - My blog, portfolio and technology related ramblings")
            assertThat(it.html).isNotBlank()
            assertThat(it.content).isNotBlank()
            assertThat(it.resolvedUrl).isEqualTo("$url/")
            assertThat(it.keywords).contains("technology", "programming", "java", "blog", "code", "software")
            assertThat(it.matches()).isTrue()

            val screen = it.generateScreenshot()
            assertThat(screen?.extension).isEqualTo(PNG)
            assertThat(screen?.image?.size).isGreaterThan(0)

            val thumb = it.generateThumbnail()
            assertThat(thumb?.extension).isEqualTo(JPG)
            assertThat(thumb?.image?.size).isGreaterThan(0)

            val print = it.printPage()
            assertThat(print?.extension).isEqualTo(PDF)
            assertThat(print?.image?.size).isGreaterThan(0)
        }
        Unit
    }

    @Test
    fun testDefaultLinkProcessorInvalidUrl() {
        assertThrows<Exception> {
            runBlocking {
                processorFactory.createProcessors("invalid.invalid").forEach {
                    it.init()
                }
            }
        }
        // 404 error
        assertThrows<Exception> {
            runBlocking {
                processorFactory.createProcessors("https://ryanharrison.co.uk/nothinghere").forEach {
                    it.init()
                }
            }
        }
    }

    @Test
    fun testImageResourceEquality() {
        val img1 = ImageResource(byteArrayOf(1,2,3,4,5), "JPG")
        val img2 = ImageResource(byteArrayOf(1,2,3,4,5), "JPG")
        assertThat(img1).isEqualTo(img2)
        assertThat(img1.hashCode()).isEqualTo(img2.hashCode())

        val img3 = ImageResource(byteArrayOf(1,2,3,4), "JPG")
        assertThat(img1).isNotEqualTo(img3)
        assertThat(img1.hashCode()).isNotEqualTo(img3.hashCode())

        val img4 = ImageResource(byteArrayOf(1,2,3,4), "PNG")
        assertThat(img4).isNotEqualTo(img3)
        assertThat(img4.hashCode()).isNotEqualTo(img3.hashCode())
    }

}