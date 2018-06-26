package worker

import common.BaseProperties
import common.Link
import common.TestCoroutineContext
import entry.LinkService
import io.mockk.*
import kotlinx.coroutines.experimental.CompletableDeferred
import kotlinx.coroutines.experimental.runBlocking
import link.ImageResource
import link.LinkProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import resource.HTML
import resource.ResourceManager
import resource.ResourceType
import suggest.Suggestion
import kotlin.coroutines.experimental.coroutineContext

class LinkProcessorWorkerTest {

    private val linkService = mockk<LinkService>()
    private val resourceManager = mockk<ResourceManager>()
    private val processorFactory = mockk<LinkProcessorFactory>()
    private val worker = LinkProcessorWorker(resourceManager, linkService).also { it.processorFactory = processorFactory }

    @Test
    fun testDefaultPersist() = runBlocking(TestCoroutineContext()) {
        val link = Link("id1", "title", "google.com", "google.com", "", 100, emptyList(), BaseProperties())

        val thumb = ImageResource(byteArrayOf(1,2,3), ".jpg")
        val screen = ImageResource(byteArrayOf(4,5,6), ".png")
        val html = "<html>"
        val content = "article content"
        val processor = mockk<LinkProcessor>()

        coEvery { processor.generateThumbnail() } returns thumb
        coEvery { processor.generateScreenshot() } returns screen
        coEvery { processor.html } returns html
        coEvery { processor.content } returns content
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
        assertThat(link.content).isEqualTo(content)

        coVerify(exactly = 1) { processor.generateThumbnail() }
        coVerify(exactly = 1) { processor.generateScreenshot() }
        coVerify(exactly = 1) { processor.html }
        coVerify(exactly = 1) { processor.content }

        verify(exactly = 1) { resourceManager.saveGeneratedResource(link.id, ResourceType.THUMBNAIL, thumb.extension, thumb.image) }
        verify(exactly = 1) { resourceManager.saveGeneratedResource(link.id, ResourceType.SCREENSHOT, screen.extension, screen.image) }
        verify(exactly = 1) { resourceManager.saveGeneratedResource(link.id, ResourceType.DOCUMENT, HTML, html.toByteArray()) }
    }

    @Test
    fun testDefaultSuggest() = runBlocking(TestCoroutineContext()) {
        val url = "google.com"

        val thumb = ImageResource(byteArrayOf(1,2,3), ".jpg")
        val screen = ImageResource(byteArrayOf(4,5,6), ".png")
        val html = "<html>"
        val processor = mockk<LinkProcessor>()

        val thumbPath = "thumbPath"
        val screenPath = "screenPath"
        val resolvedUrl = "resolvedUrl"
        val title = "title"

        coEvery { processor.generateThumbnail() } returns thumb
        coEvery { processor.generateScreenshot() } returns screen
        coEvery { processor.html } returns html
        every { processor.title } returns title
        every { processor.resolvedUrl } returns resolvedUrl
        every { processor.close() } just Runs

        coEvery { processorFactory.createProcessors(url) } returns listOf(processor)
        every { resourceManager.saveTempFile(url, thumb.image, ResourceType.THUMBNAIL, thumb.extension) } returns thumbPath
        every { resourceManager.saveTempFile(url, screen.image, ResourceType.SCREENSHOT, screen.extension) } returns screenPath
        every { resourceManager.saveTempFile(url, html.toByteArray(), ResourceType.DOCUMENT, HTML) } returns screenPath

        val deferred = CompletableDeferred<Suggestion>()
        val channel = worker.apply { runner = coroutineContext }.worker()
        channel.send(SuggestLinkProcessingRequest(url, deferred))
        channel.close()

        val suggestion = deferred.await()
        assertThat(suggestion.title).isEqualTo(title)
        assertThat(suggestion.url).isEqualTo(resolvedUrl)
        assertThat(suggestion.thumbnail).isEqualTo(thumbPath)
        assertThat(suggestion.screenshot).isEqualTo(screenPath)

        coVerify(exactly = 1) { processorFactory.createProcessors(url) }
        verify(exactly = 1) { processor.close() }

        coVerify(exactly = 1) { processor.generateThumbnail() }
        coVerify(exactly = 1) { processor.generateScreenshot() }
        coVerify(exactly = 1) { processor.html }

        verify(exactly = 1) { resourceManager.saveTempFile(url, thumb.image, ResourceType.THUMBNAIL, thumb.extension) }
        verify(exactly = 1) { resourceManager.saveTempFile(url,screen.image, ResourceType.SCREENSHOT, screen.extension) }
        verify(exactly = 1) { resourceManager.saveTempFile(url, html.toByteArray(), ResourceType.DOCUMENT, HTML) }
    }

    @Test
    fun testSuggestCompletedExceptionally() = runBlocking(TestCoroutineContext()) {
        val url = "google.com"

        val processor = mockk<LinkProcessor>()

        val exception = RuntimeException("error during computation")
        coEvery { processor.generateThumbnail() } throws exception
        every { processor.close() } just Runs

        coEvery { processorFactory.createProcessors(url) } returns listOf(processor)

        val deferred = CompletableDeferred<Suggestion>()
        val channel = worker.apply { runner = coroutineContext }.worker()
        channel.send(SuggestLinkProcessingRequest(url, deferred))
        channel.close()

        assertThat(deferred.isCompletedExceptionally).isTrue()
        assertThat(deferred.getCompletionExceptionOrNull()).isEqualTo(exception)

        coVerify(exactly = 1) { processorFactory.createProcessors(url) }
        verify(exactly = 1) { processor.close() }

        coVerify(exactly = 1) { processor.generateThumbnail() }
        coVerify(exactly = 1) { processor.generateScreenshot() }
        coVerify(exactly = 0) { processor.html }
        verify(exactly = 0) { resourceManager.saveTempFile(any(), any(), any(), any()) }
    }
}