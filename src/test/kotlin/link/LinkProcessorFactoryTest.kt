package link

import link.extract.ExtractionPolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LinkProcessorFactoryTest {

    private val processorFactory = LinkProcessorFactory()

    @Test
    fun testCreateYoutubeProcessor() {
        val url = "https://youtube.com/something"
        val processors = processorFactory.createProcessors(url, ExtractionPolicy.FULL)
        assertThat(processors).hasSize(1)
        assertThat(processors).hasOnlyElementsOfType(YoutubeLinkProcessor::class.java)
        assertThat(processors.first().resolvedUrl).isEqualTo(url)
    }

    @Test
    fun testCreatDefaultProcessor() {
        val url = "https://ryanharrison.co.uk"
        val processors = processorFactory.createProcessors(url, ExtractionPolicy.PARTIAL)
        assertThat(processors).hasSize(1)
        assertThat(processors).hasOnlyElementsOfType(DefaultLinkProcessor::class.java)
        assertThat(processors.first().resolvedUrl).isEqualTo(url)
    }

}
