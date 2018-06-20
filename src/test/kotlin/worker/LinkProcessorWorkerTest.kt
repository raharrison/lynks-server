package worker

import common.BaseProperties
import common.Link
import common.TestCoroutineContext
import entry.LinkService
import io.mockk.*
import kotlinx.coroutines.experimental.runBlocking
import link.ImageResource
import link.LinkProcessor
import org.junit.jupiter.api.Test
import resource.HTML
import resource.ResourceManager
import resource.ResourceType

class LinkProcessorWorkerTest {

    private val linkService = mockk<LinkService>()
    private val resourceManager = mockk<ResourceManager>()
    private val processorFactory = mockk<LinkProcessorFactory>()
    private val worker = LinkProcessorWorker(resourceManager, linkService).also { it.processorFactory = processorFactory }

    @Test
    fun testDefaultPersist() = runBlocking(TestCoroutineContext()) {
        val link = Link("id1", "title", "google.com", "google.com", 100, emptyList(), BaseProperties())

        val thumb = ImageResource(byteArrayOf(1,2,3), ".jpg")
        val screen = ImageResource(byteArrayOf(4,5,6), ".png")
        val html = "<html>"
        val processor = mockk<LinkProcessor>()

        coEvery { processor.generateThumbnail() } returns thumb
        coEvery { processor.generateScreenshot() } returns screen
        coEvery { processor.html } returns html
        coEvery { processor.enrich(link.props) } just Runs
        every { processor.close() } just Runs

        coEvery { processorFactory.createProcessors(link.url) } returns listOf(processor)
        every { resourceManager.saveGeneratedResource(link.id, any(), any(), any()) } just Runs
        every { linkService.update(link) } returns link

        val channel = worker.apply { runner = coroutineContext }.worker()
        channel.send(PersistLinkProcessingRequest(link))
        channel.close()

        coVerify(exactly = 1) { processorFactory.createProcessors(link.url) }
        verify(exactly = 1) { processor.close() }
        verify(exactly = 1) { linkService.update(link) }

        coVerify(exactly = 1) { processor.generateThumbnail() }
        coVerify(exactly = 1) { processor.generateScreenshot() }
        coVerify(exactly = 1) { processor.html }

        verify(exactly = 1) { resourceManager.saveGeneratedResource(link.id, ResourceType.THUMBNAIL, thumb.extension, thumb.image) }
        verify(exactly = 1) { resourceManager.saveGeneratedResource(link.id, ResourceType.SCREENSHOT, screen.extension, screen.image) }
        verify(exactly = 1) { resourceManager.saveGeneratedResource(link.id, ResourceType.DOCUMENT, HTML, html.toByteArray()) }
    }

}