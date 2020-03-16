package worker

import common.Link
import common.TestCoroutineContext
import entry.LinkService
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import link.ImageResource
import link.LinkProcessor
import notify.NotifyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import resource.HTML
import resource.Resource
import resource.ResourceManager
import resource.ResourceType
import suggest.Suggestion

class LinkProcessorWorkerTest {

    private val linkService = mockk<LinkService>()
    private val resourceManager = mockk<ResourceManager>()
    private val processorFactory = mockk<LinkProcessorFactory>()
    private val notifyService = mockk<NotifyService>(relaxUnitFun = true)
    private val worker = LinkProcessorWorker(resourceManager, linkService, notifyService).also { it.processorFactory = processorFactory }

    @Test
    fun testDefaultPersist() = runBlocking(TestCoroutineContext()) {
        val link = Link("id1", "title", "google.com", "google.com", "", 100)

        val thumb = ImageResource(byteArrayOf(1,2,3), "jpg")
        val screen = ImageResource(byteArrayOf(4,5,6), "png")
        val html = "<html>"
        val content = "article content"
        val processor = mockk<LinkProcessor>()

        coEvery { notifyService.accept(any(), ofType(Link::class)) } just Runs
        coEvery { processor.generateThumbnail() } returns thumb
        coEvery { processor.generateScreenshot() } returns screen
        coEvery { processor.html } returns html
        coEvery { processor.content } returns content
        coEvery { processor.enrich(link.props) } just Runs
        every { processor.close() } just Runs

        coEvery { processorFactory.createProcessors(link.url) } returns listOf(processor)
        every { resourceManager.saveGeneratedResource(link.id, any(), any(), any()) } returns Resource("rid", "eid", "file1.txt", "txt", ResourceType.UPLOAD, 12L, 12L, 12L)
        every { linkService.update(link) } returns link
        every { linkService.mergeProps(eq("id1"), any()) } just Runs

        val channel = worker.apply { runner = this@runBlocking.coroutineContext }.worker()
        channel.send(PersistLinkProcessingRequest(link))
        channel.close()

        coVerify(exactly = 1) { processorFactory.createProcessors(link.url) }
        verify(exactly = 1) { processor.close() }
        verify(exactly = 1) { linkService.mergeProps(eq("id1"), any()) }
        verify(exactly = 1) { linkService.update(link) }
        coVerify(exactly = 1) { notifyService.accept(any(), ofType(Link::class)) }
        assertThat(link.content).isEqualTo(content)

        coVerify(exactly = 1) { processor.generateThumbnail() }
        coVerify(exactly = 1) { processor.generateScreenshot() }
        coVerify(exactly = 1) { processor.html }
        coVerify(exactly = 1) { processor.content }

        verify(exactly = 1) { resourceManager.saveGeneratedResource(link.id, "thumbnail.jpg", ResourceType.THUMBNAIL, thumb.image) }
        verify(exactly = 1) { resourceManager.saveGeneratedResource(link.id, "screenshot.png", ResourceType.SCREENSHOT, screen.image) }
        verify(exactly = 1) { resourceManager.saveGeneratedResource(link.id, "document.html", ResourceType.DOCUMENT, html.toByteArray()) }
    }

    @Test
    fun testDefaultPersistCompletedExceptionally() = runBlocking(TestCoroutineContext()) {
        val link = Link("id1", "title", "google.com", "google.com", "", 100)

        val exception = RuntimeException("error during computation")
        val processor = mockk<LinkProcessor>()
        coEvery { notifyService.accept(any(), null) } just Runs
        coEvery { processor.generateThumbnail() } throws exception
        coEvery{ processor.generateScreenshot()} returns null
        every { processor.close() } just Runs

        coEvery { processorFactory.createProcessors(link.url) } returns listOf(processor)
        every { linkService.update(link) } returns link
        every { linkService.mergeProps(eq("id1"), any()) } just Runs

        val channel = worker.apply { runner = this@runBlocking.coroutineContext }.worker()
        channel.send(PersistLinkProcessingRequest(link))
        channel.close()

        coVerify(exactly = 1) { processorFactory.createProcessors(link.url) }
        verify(exactly = 1) { processor.close() }
        verify(exactly = 0) { linkService.update(link) }
        verify(exactly = 1) { linkService.mergeProps(eq("id1"), any()) }
        coVerify(exactly = 1) { notifyService.accept(any(), null) }
        assertThat(link.props.containsAttribute("dead")).isTrue()

        Unit
    }

    @Test
    fun testDefaultSuggest() = runBlocking(TestCoroutineContext()) {
        val url = "google.com"

        val thumb = ImageResource(byteArrayOf(1,2,3), "jpg")
        val screen = ImageResource(byteArrayOf(4,5,6), "png")
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
        val channel = worker.apply { runner = this@runBlocking.coroutineContext }.worker()
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
        val channel = worker.apply { runner = this@runBlocking.coroutineContext }.worker()
        channel.send(SuggestLinkProcessingRequest(url, deferred))
        channel.close()

        assertThat(deferred.getCompletionExceptionOrNull()?.message).isEqualTo(exception.message)

        coVerify(exactly = 1) { processorFactory.createProcessors(url) }
        verify(exactly = 1) { processor.close() }

        coVerify(exactly = 1) { processor.generateThumbnail() }
        coVerify(exactly = 0) { processor.html }
        verify(exactly = 0) { resourceManager.saveTempFile(any(), any(), any(), any()) }
    }
}