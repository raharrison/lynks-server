package worker

import common.Link
import common.TestCoroutineContext
import entry.EntryAuditService
import entry.LinkService
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import link.ImageResource
import link.LinkProcessor
import notify.NotifyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import resource.HTML
import resource.Resource
import resource.ResourceManager
import resource.ResourceType
import suggest.Suggestion
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

class LinkProcessorWorkerTest {

    private val resourcePath = Paths.get("temp.txt")
    private val linkService = mockk<LinkService>()
    private val resourceManager = mockk<ResourceManager>()
    private val processorFactory = mockk<LinkProcessorFactory>()
    private val notifyService = mockk<NotifyService>(relaxUnitFun = true)
    private val entryAuditService = mockk<EntryAuditService>(relaxUnitFun = true)
    private val worker =
        LinkProcessorWorker(resourceManager, linkService, notifyService, entryAuditService)
            .also { it.processorFactory = processorFactory }

    @AfterEach
    fun clean() {
        if(Files.exists(resourcePath)) {
            Files.delete(resourcePath)
        }
    }

    @Test
    fun testDefaultPersistAllTypes() = runBlocking(TestCoroutineContext()) {
        val link = Link("id1", "title", "google.com", "google.com", "", 100, 100)

        val thumb = ImageResource(byteArrayOf(1, 2, 3), "jpg")
        val screen = ImageResource(byteArrayOf(4, 5, 6), "png")
        val html = "<html>"
        val content = "article content"
        val processor = mockk<LinkProcessor>(relaxUnitFun = true)

        coEvery { notifyService.accept(any(), ofType(Link::class)) } just Runs
        coEvery { processor.generateThumbnail() } returns thumb
        coEvery { processor.generateScreenshot() } returns screen
        coEvery { processor.html } returns html
        coEvery { processor.content } returns content
        coEvery { processor.enrich(link.props) } just Runs
        every { processor.close() } just Runs

        coEvery { processorFactory.createProcessors(link.url) } returns listOf(processor)
        every { resourceManager.saveGeneratedResource(link.id, any(), any(), any()) } returns Resource(
            "rid",
            "eid",
            "file1.txt",
            "txt",
            ResourceType.GENERATED,
            12L,
            12L,
            12L
        )
        every { linkService.update(link) } returns link
        every { linkService.mergeProps(eq("id1"), any()) } just Runs

        every { resourceManager.moveTempFiles(link.id, link.url) } returns emptyList()

        val channel = worker.apply { runner = this@runBlocking.coroutineContext }.worker()
        channel.send(PersistLinkProcessingRequest(link, ResourceType.all(), true))
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

        verify(exactly = 1) {
            resourceManager.saveGeneratedResource(
                link.id,
                match { it.startsWith("thumbnail") },
                ResourceType.THUMBNAIL,
                thumb.image
            )
        }
        verify(exactly = 1) {
            resourceManager.saveGeneratedResource(
                link.id,
                match { it.startsWith("screenshot") },
                ResourceType.SCREENSHOT,
                screen.image
            )
        }
        verify(exactly = 1) {
            resourceManager.saveGeneratedResource(
                link.id,
                match { it.startsWith("document") },
                ResourceType.DOCUMENT,
                html.toByteArray()
            )
        }
        verify(exactly = 1) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
    }

    @Test
    fun testDefaultPersistSingleType() = runBlocking(TestCoroutineContext()) {
        val link = Link("id1", "title", "google.com", "google.com", "", 100, 100)

        val screen = ImageResource(byteArrayOf(4, 5, 6), "png")
        val processor = mockk<LinkProcessor>(relaxUnitFun = true)

        coEvery { notifyService.accept(any(), ofType(Link::class)) } just Runs
        coEvery { processor.generateScreenshot() } returns screen
        coEvery { processor.enrich(link.props) } just Runs
        every { processor.close() } just Runs

        coEvery { processorFactory.createProcessors(link.url) } returns listOf(processor)
        every { resourceManager.saveGeneratedResource(link.id, any(), any(), any()) } returns Resource(
            "rid",
            "eid",
            "file1.txt",
            "txt",
            ResourceType.GENERATED,
            12L,
            12L,
            12L
        )
        every { linkService.update(link) } returns link
        every { linkService.mergeProps(eq("id1"), any()) } just Runs

        every { resourceManager.moveTempFiles(link.id, link.url) } returns emptyList()

        val channel = worker.apply { runner = this@runBlocking.coroutineContext }.worker()
        channel.send(PersistLinkProcessingRequest(link, EnumSet.of(ResourceType.SCREENSHOT), true))
        channel.close()

        coVerify(exactly = 1) { processorFactory.createProcessors(link.url) }
        verify(exactly = 1) { processor.close() }
        verify(exactly = 1) { linkService.mergeProps(eq("id1"), any()) }
        verify(exactly = 1) { linkService.update(link) }
        coVerify(exactly = 1) { notifyService.accept(any(), ofType(Link::class)) }

        coVerify(exactly = 0) { processor.generateThumbnail() }
        coVerify(exactly = 1) { processor.generateScreenshot() }
        coVerify(exactly = 0) { processor.html }
        coVerify(exactly = 0) { processor.content }

        verify(exactly = 0) {
            resourceManager.saveGeneratedResource(link.id, any(), ResourceType.THUMBNAIL, any())
        }
        verify(exactly = 0) {
            resourceManager.saveGeneratedResource(link.id, any(), ResourceType.DOCUMENT, any())
        }
        verify(exactly = 1) {
            resourceManager.saveGeneratedResource(
                link.id,
                match { it.startsWith("screenshot") },
                ResourceType.SCREENSHOT,
                screen.image
            )
        }
        verify(exactly = 1) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
    }

    @Test
    fun testDefaultPersistAlreadyProcessed() = runBlocking(TestCoroutineContext()) {
        val link = Link("id1", "title", "google.com", "google.com", "", 100, 100)

        val docResource = Resource("rid", "id1","name", "html", ResourceType.DOCUMENT, 10, 123, 123)
        withContext(Dispatchers.IO) {
            Files.write(resourcePath, byteArrayOf(1, 2))
        }

        val processor = mockk<LinkProcessor>(relaxUnitFun = true)
        coEvery { notifyService.accept(any(), ofType(Link::class)) } just Runs
        coEvery { processor.enrich(link.props) } just Runs
        every { processor.close() } just Runs

        coEvery { processorFactory.createProcessors(link.url) } returns listOf(processor)
        every { linkService.update(link) } returns link
        every { linkService.mergeProps(eq("id1"), any()) } just Runs

        every { resourceManager.moveTempFiles(link.id, link.url) } returns listOf(docResource)
        every { resourceManager.getResourceAsFile(docResource.id) } returns Pair(docResource, resourcePath.toFile())

        val channel = worker.apply { runner = this@runBlocking.coroutineContext }.worker()
        channel.send(PersistLinkProcessingRequest(link, ResourceType.all(), true))
        channel.close()

        coVerify(exactly = 1) { processorFactory.createProcessors(link.url) }
        verify(exactly = 1) { processor.close() }
        verify(exactly = 1) { linkService.mergeProps(eq("id1"), any()) }
        verify(exactly = 1) { linkService.update(link) }
        coVerify(exactly = 1) { notifyService.accept(any(), ofType(Link::class)) }

        coVerify(exactly = 0) { processor.generateThumbnail() }
        coVerify(exactly = 0) { processor.generateScreenshot() }
        coVerify(exactly = 0) { processor.html }
        coVerify(exactly = 0) { processor.content }
        verify(exactly = 0) { resourceManager.saveGeneratedResource(any(), any(), any(), any()) }
        verify(exactly = 1) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
    }

    @Test
    fun testDefaultPersistNoProcessFlag() = runBlocking(TestCoroutineContext()) {
        val link = Link("id1", "title", "google.com", "google.com", "", 100, 100)

        val processor = mockk<LinkProcessor>(relaxUnitFun = true)
        coEvery { notifyService.accept(any(), ofType(Link::class)) } just Runs
        coEvery { processor.enrich(link.props) } just Runs
        every { processor.close() } just Runs

        coEvery { processorFactory.createProcessors(link.url) } returns listOf(processor)
        every { linkService.update(link) } returns link
        every { linkService.mergeProps(eq("id1"), any()) } just Runs

        every { resourceManager.moveTempFiles(link.id, link.url) } returns emptyList()

        val channel = worker.apply { runner = this@runBlocking.coroutineContext }.worker()
        channel.send(PersistLinkProcessingRequest(link, ResourceType.all(), false))
        channel.close()

        coVerify(exactly = 1) { processorFactory.createProcessors(link.url) }
        verify(exactly = 1) { processor.close() }
        verify(exactly = 1) { linkService.mergeProps(eq("id1"), any()) }
        verify(exactly = 1) { linkService.update(link) }
        coVerify(exactly = 0) { notifyService.accept(any(), ofType(Link::class)) }

        coVerify(exactly = 0) { processor.generateThumbnail() }
        coVerify(exactly = 0) { processor.generateScreenshot() }
        coVerify(exactly = 0) { processor.html }
        coVerify(exactly = 0) { processor.content }
        verify(exactly = 0) { resourceManager.saveGeneratedResource(any(), any(), any(), any()) }
        verify(exactly = 0) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
    }

    @Test
    fun testDefaultPersistCompletedExceptionally() = runBlocking(TestCoroutineContext()) {
        val link = Link("id1", "title", "google.com", "google.com", "", 100, 100)

        val exception = RuntimeException("error during computation")
        val processor = mockk<LinkProcessor>(relaxUnitFun = true)
        coEvery { notifyService.accept(any(), null) } just Runs
        coEvery { processor.generateThumbnail() } throws exception
        coEvery { processor.generateScreenshot() } returns null
        every { processor.close() } just Runs

        coEvery { processorFactory.createProcessors(link.url) } returns listOf(processor)
        every { linkService.update(link) } returns link
        every { linkService.mergeProps(eq("id1"), any()) } just Runs

        every { resourceManager.moveTempFiles(link.id, link.url) } returns emptyList()

        val channel = worker.apply { runner = this@runBlocking.coroutineContext }.worker()
        channel.send(PersistLinkProcessingRequest(link, ResourceType.all(), true))
        channel.close()

        coVerify(exactly = 1) { processorFactory.createProcessors(link.url) }
        verify(exactly = 1) { processor.close() }
        verify(exactly = 0) { linkService.update(link) }
        verify(exactly = 1) { linkService.mergeProps(eq("id1"), any()) }
        coVerify(exactly = 1) { notifyService.accept(any(), null) }
        verify(exactly = 1) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
        assertThat(link.props.containsAttribute("dead")).isTrue()

        Unit
    }

    @Test
    fun testDefaultSuggest() = runBlocking(TestCoroutineContext()) {
        val url = "google.com"

        val thumb = ImageResource(byteArrayOf(1, 2, 3), "jpg")
        val screen = ImageResource(byteArrayOf(4, 5, 6), "png")
        val html = "<html>"
        val processor = mockk<LinkProcessor>(relaxUnitFun = true)

        val thumbPath = "thumbPath"
        val screenPath = "screenPath"
        val resolvedUrl = "resolvedUrl"
        val title = "title"
        val keywords = setOf("search", "other", "important")

        coEvery { processor.generateThumbnail() } returns thumb
        coEvery { processor.generateScreenshot() } returns screen
        coEvery { processor.html } returns html
        every { processor.title } returns title
        every { processor.resolvedUrl } returns resolvedUrl
        every { processor.keywords } returns keywords
        every { processor.close() } just Runs

        coEvery { processorFactory.createProcessors(url) } returns listOf(processor)
        every {
            resourceManager.saveTempFile(
                url,
                thumb.image,
                ResourceType.THUMBNAIL,
                thumb.extension
            )
        } returns thumbPath
        every {
            resourceManager.saveTempFile(
                url,
                screen.image,
                ResourceType.SCREENSHOT,
                screen.extension
            )
        } returns screenPath
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
        assertThat(suggestion.keywords).isEqualTo(keywords)

        coVerify(exactly = 1) { processorFactory.createProcessors(url) }
        verify(exactly = 1) { processor.close() }

        coVerify(exactly = 1) { processor.generateThumbnail() }
        coVerify(exactly = 1) { processor.generateScreenshot() }
        coVerify(exactly = 1) { processor.html }
        coVerify(exactly = 1) { processor.keywords }

        verify(exactly = 1) { resourceManager.saveTempFile(url, thumb.image, ResourceType.THUMBNAIL, thumb.extension) }
        verify(exactly = 1) {
            resourceManager.saveTempFile(
                url,
                screen.image,
                ResourceType.SCREENSHOT,
                screen.extension
            )
        }
        verify(exactly = 1) { resourceManager.saveTempFile(url, html.toByteArray(), ResourceType.DOCUMENT, HTML) }
    }

    @Test
    fun testSuggestCompletedExceptionally() = runBlocking(TestCoroutineContext()) {
        val url = "google.com"

        val processor = mockk<LinkProcessor>(relaxUnitFun = true)

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

    @Test
    fun testDefaultActiveCheck() = runBlocking(TestCoroutineContext()) {
        val url = "google.com"

        val processor = mockk<LinkProcessor>(relaxUnitFun = true)
        every { processor.close() } just Runs

        coEvery { processorFactory.createProcessors(url) } returns listOf(processor)

        val deferred = CompletableDeferred<Boolean>()
        val channel = worker.apply { runner = this@runBlocking.coroutineContext }.worker()
        channel.send(ActiveLinkCheckingRequest(url, deferred))
        channel.close()

        val active = deferred.await()
        assertThat(active).isTrue()

        coVerify(exactly = 1) { processorFactory.createProcessors(url) }
        verify(exactly = 1) { processor.close() }
    }

    @Test
    fun testDefaultActiveCheckFoundDeadLink() = runBlocking(TestCoroutineContext()) {
        val url = "google.com"

        val processor = mockk<LinkProcessor>(relaxUnitFun = true)

        val exception = RuntimeException("error during computation")
        coEvery { processor.init() } throws exception
        every { processor.close() } just Runs

        coEvery { processorFactory.createProcessors(url) } returns listOf(processor)

        val deferred = CompletableDeferred<Boolean>()
        val channel = worker.apply { runner = this@runBlocking.coroutineContext }.worker()
        channel.send(ActiveLinkCheckingRequest(url, deferred))
        channel.close()

        val active = deferred.await()
        assertThat(active).isFalse()

        coVerify(exactly = 1) { processorFactory.createProcessors(url) }
        verify(exactly = 1) { processor.close() }
    }
}