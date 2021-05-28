package link

import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import resource.ResourceManager

class LinkProcessorFactoryTest {

    private val resourceManager = mockk<ResourceManager>()
    private val processorFactory = LinkProcessorFactory(resourceManager = resourceManager)

    @Test
    fun testCreateYoutubeProcessor() {
        val url = "https://youtube.com/something"
        val processors = processorFactory.createProcessors(url)
        assertThat(processors).hasSize(1)
        assertThat(processors).hasOnlyElementsOfType(YoutubeLinkProcessor::class.java)
        assertThat(processors.first().url).isEqualTo(url)
    }

    @Test
    fun testCreatDefaultProcessor() {
        val url = "https://ryanharrison.co.uk"
        val processors = processorFactory.createProcessors(url)
        assertThat(processors).hasSize(1)
        assertThat(processors).hasOnlyElementsOfType(DefaultLinkProcessor::class.java)
        assertThat(processors.first().url).isEqualTo(url)
    }

}
