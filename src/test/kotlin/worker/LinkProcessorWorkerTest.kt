package worker

import common.Link
import common.TestCoroutineContext
import entry.EntryAuditService
import entry.LinkService
import group.Collection
import group.GroupSet
import group.GroupSetService
import group.Tag
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import link.GeneratedDocResource
import link.GeneratedImageResource
import link.LinkProcessor
import link.LinkProcessorFactory
import link.extract.ExtractionPolicy
import link.extract.LinkContent
import notify.NotifyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
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
    private val groupSetService = mockk<GroupSetService>()
    private val processorFactory = mockk<LinkProcessorFactory>()
    private val notifyService = mockk<NotifyService>(relaxUnitFun = true)
    private val entryAuditService = mockk<EntryAuditService>(relaxUnitFun = true)
    private val worker =
        LinkProcessorWorker(resourceManager, linkService, groupSetService, notifyService, entryAuditService)
            .also { it.processorFactory = processorFactory }

    @AfterEach
    fun clean() {
        if(Files.exists(resourcePath)) {
            Files.delete(resourcePath)
        }
    }

    @Nested
    inner class Persist {
        @Test
        fun testDefaultPersistAllTypes() = runBlocking(TestCoroutineContext()) {
            val link = Link("id1", "title", "google.com", "google.com", "", 100, 100)
            val resourceSet = ResourceType.all()

            val thumb = GeneratedImageResource(byteArrayOf(1, 2, 3), "jpg")
            val screen = GeneratedImageResource(byteArrayOf(4, 5, 6), "png")
            val html = GeneratedDocResource("<html><body><p>article content<p></body></html>", HTML)
            val processor = mockk<LinkProcessor>(relaxUnitFun = true)

            coEvery { notifyService.accept(any(), ofType(Link::class)) } just Runs
            coEvery { processor.process(resourceSet) } returns mapOf(
                ResourceType.THUMBNAIL to thumb,
                ResourceType.SCREENSHOT to screen,
                ResourceType.PAGE to html,
                ResourceType.READABLE to html
            )
            coEvery { processor.enrich(link.props) } just Runs
            every { processor.close() } just Runs

            coEvery { processorFactory.createProcessors(link.url, ExtractionPolicy.FULL) } returns listOf(processor)
            every { resourceManager.deleteTempFiles(link.url) } just Runs
            every { resourceManager.saveGeneratedResource(link.id, any(), any(), any()) } answers {
                Resource(
                    "rid",
                    firstArg(),
                    secondArg(),
                    "txt",
                    thirdArg(),
                    12L,
                    12L,
                    12L
                )
            }
            link.thumbnailId = "rid"
            every { linkService.update(link) } returns link
            every { linkService.mergeProps(eq("id1"), any()) } just Runs

            val channel = worker.apply { runner = this@runBlocking.coroutineContext }.worker()
            channel.send(PersistLinkProcessingRequest(link, resourceSet, true))
            channel.close()

            coVerify(exactly = 1) { processorFactory.createProcessors(link.url, ExtractionPolicy.FULL) }
            verify(exactly = 1) { processor.close() }
            verify(exactly = 1) { linkService.mergeProps(eq("id1"), any()) }
            verify(exactly = 1) { linkService.update(link) }
            coVerify(exactly = 1) { notifyService.accept(any(), ofType(Link::class)) }
            assertThat(link.content).isEqualTo("article content")

            coVerify(exactly = 1) { processor.process(resourceSet) }

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
                    match { it.startsWith("page") },
                    ResourceType.PAGE,
                    html.doc.toByteArray()
                )
            }
            verify(exactly = 1) {
                resourceManager.saveGeneratedResource(
                    link.id,
                    match { it.startsWith("readable") },
                    ResourceType.READABLE,
                    html.doc.toByteArray()
                )
            }
            verify(exactly = 1) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
        }

        @Test
        fun testDefaultPersistSingleType() = runBlocking(TestCoroutineContext()) {
            val link = Link("id1", "title", "google.com", "google.com", "", 100, 100)
            val resourceSet = EnumSet.of(ResourceType.SCREENSHOT)

            val screen = GeneratedImageResource(byteArrayOf(4, 5, 6), "png")
            val processor = mockk<LinkProcessor>(relaxUnitFun = true)

            coEvery { notifyService.accept(any(), ofType(Link::class)) } just Runs
            coEvery { processor.process(resourceSet) } returns mapOf(
                ResourceType.SCREENSHOT to screen
            )
            coEvery { processor.enrich(link.props) } just Runs
            every { processor.close() } just Runs

            coEvery { processorFactory.createProcessors(link.url, ExtractionPolicy.FULL) } returns listOf(processor)
            every { resourceManager.deleteTempFiles(link.url) } just Runs
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

            val channel = worker.apply { runner = this@runBlocking.coroutineContext }.worker()
            channel.send(PersistLinkProcessingRequest(link, resourceSet, true))
            channel.close()

            coVerify(exactly = 1) { processorFactory.createProcessors(link.url, ExtractionPolicy.FULL) }
            verify(exactly = 1) { processor.close() }
            verify(exactly = 1) { linkService.mergeProps(eq("id1"), any()) }
            verify(exactly = 1) { linkService.update(link) }
            coVerify(exactly = 1) { notifyService.accept(any(), ofType(Link::class)) }

            coVerify(exactly = 1) { processor.process(resourceSet) }

            verify(exactly = 0) {
                resourceManager.saveGeneratedResource(link.id, any(), ResourceType.THUMBNAIL, any())
            }
            verify(exactly = 0) {
                resourceManager.saveGeneratedResource(link.id, any(), ResourceType.DOCUMENT, any())
            }
            verify(exactly = 0) {
                resourceManager.saveGeneratedResource(link.id, any(), ResourceType.READABLE, any())
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
        fun testDefaultPersistNoProcessFlag() = runBlocking(TestCoroutineContext()) {
            val link = Link("id1", "title", "google.com", "google.com", "", 100, 100)

            val processor = mockk<LinkProcessor>(relaxUnitFun = true)
            coEvery { notifyService.accept(any(), ofType(Link::class)) } just Runs
            coEvery { processor.enrich(link.props) } just Runs
            every { processor.close() } just Runs

            coEvery { processorFactory.createProcessors(link.url, ExtractionPolicy.FULL) } returns listOf(processor)
            every { linkService.update(link) } returns link
            every { linkService.mergeProps(eq("id1"), any()) } just Runs
            every { resourceManager.deleteTempFiles(link.url) } just Runs

            val channel = worker.apply { runner = this@runBlocking.coroutineContext }.worker()
            channel.send(PersistLinkProcessingRequest(link, ResourceType.all(), false))
            channel.close()

            coVerify(exactly = 1) { processorFactory.createProcessors(link.url, ExtractionPolicy.FULL) }
            verify(exactly = 1) { processor.close() }
            verify(exactly = 1) { linkService.mergeProps(eq("id1"), any()) }
            verify(exactly = 1) { linkService.update(link) }
            coVerify(exactly = 0) { notifyService.accept(any(), ofType(Link::class)) }

            coVerify(exactly = 0) { processor.process(any()) }
            verify(exactly = 0) { resourceManager.saveGeneratedResource(any(), any(), any(), any()) }
            verify(exactly = 0) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
        }

        @Test
        fun testDefaultPersistNoResourceTypes() = runBlocking(TestCoroutineContext()) {
            val link = Link("id1", "title", "google.com", "google.com", "", 100, 100)

            val processor = mockk<LinkProcessor>(relaxUnitFun = true)

            coEvery { processorFactory.createProcessors(link.url, ExtractionPolicy.FULL) } returns listOf(processor)
            every { linkService.update(link) } returns link
            every { linkService.mergeProps(eq("id1"), any()) } just Runs
            every { resourceManager.deleteTempFiles(link.url) } just Runs

            val channel = worker.apply { runner = this@runBlocking.coroutineContext }.worker()
            channel.send(PersistLinkProcessingRequest(link, EnumSet.noneOf(ResourceType::class.java), true))
            channel.close()

            coVerify(exactly = 1) { processorFactory.createProcessors(link.url, ExtractionPolicy.FULL) }
            verify(exactly = 1) { processor.close() }
            verify(exactly = 1) { linkService.mergeProps(eq("id1"), any()) }
            verify(exactly = 1) { linkService.update(link) }
            coVerify(exactly = 1) { notifyService.accept(any(), ofType(Link::class)) }

            coVerify(exactly = 0) { processor.process(any()) }
            verify(exactly = 0) { resourceManager.saveGeneratedResource(any(), any(), any(), any()) }
            verify(exactly = 1) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
        }

        @Test
        fun testDefaultPersistCompletedExceptionally() = runBlocking(TestCoroutineContext()) {
            val link = Link("id1", "title", "google.com", "google.com", "", 100, 100)

            val exception = RuntimeException("error during computation")
            val processor = mockk<LinkProcessor>(relaxUnitFun = true)
            coEvery { notifyService.accept(any(), null) } just Runs
            coEvery { processor.process(any()) } throws exception
            every { processor.close() } just Runs

            coEvery { processorFactory.createProcessors(link.url, ExtractionPolicy.FULL) } returns listOf(processor)
            every { linkService.update(link) } returns link
            every { linkService.mergeProps(eq("id1"), any()) } just Runs
            every { resourceManager.deleteTempFiles(link.url) } just Runs

            val channel = worker.apply { runner = this@runBlocking.coroutineContext }.worker()
            channel.send(PersistLinkProcessingRequest(link, ResourceType.all(), true))
            channel.close()

            coVerify(exactly = 1) { processorFactory.createProcessors(link.url, ExtractionPolicy.FULL) }
            verify(exactly = 1) { processor.close() }
            verify(exactly = 0) { linkService.update(link) }
            verify(exactly = 1) { linkService.mergeProps(eq("id1"), any()) }
            coVerify(exactly = 1) { notifyService.accept(any(), null) }
            verify(exactly = 1) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
            assertThat(link.props.containsAttribute("dead")).isTrue()

            Unit
        }
    }

    @Nested
    inner class Suggest {
        @Test
        fun testDefaultSuggest() = runBlocking(TestCoroutineContext()) {
            val url = "google.com"
            val resourceSet = EnumSet.of(ResourceType.PREVIEW, ResourceType.THUMBNAIL)
            val thumb = GeneratedImageResource(byteArrayOf(1, 2, 3), "jpg")
            val screen = GeneratedImageResource(byteArrayOf(4, 5, 6), "png")
            val processor = mockk<LinkProcessor>(relaxUnitFun = true)

            val thumbPath = "thumbPath"
            val screenPath = "screenPath"
            val resolvedUrl = "resolvedUrl"
            val content = "article content"
            val title = "title"
            val keywords = setOf("search", "other", "important")
            val tags = listOf(
                Tag("t1", "tag1", 124L, 1234L),
                Tag("t2", "tag2", 124L, 1234L)
            )
            val collections = listOf(
                Collection("c1", "col1", mutableSetOf(), 124L, 1234L),
                Collection("c2", "col2", mutableSetOf(), 124L, 1234L)
            )
            val linkContent = LinkContent(title, content, content, "imgUrl", keywords)

            coEvery { processor.process(resourceSet) } returns mapOf(
                ResourceType.THUMBNAIL to thumb,
                ResourceType.PREVIEW to screen
            )
            coEvery { processor.linkContent } returns linkContent
            every { processor.resolvedUrl } returns resolvedUrl
            every { processor.close() } just Runs
            every { groupSetService.matchWithContent(content) } returns GroupSet(tags, collections)

            coEvery { processorFactory.createProcessors(url, ExtractionPolicy.PARTIAL) } returns listOf(processor)
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
                    ResourceType.PREVIEW,
                    screen.extension
                )
            } returns screenPath

            val deferred = CompletableDeferred<Suggestion>()
            val channel = worker.apply { runner = this@runBlocking.coroutineContext }.worker()
            channel.send(SuggestLinkProcessingRequest(url, deferred))
            channel.close()

            val suggestion = deferred.await()
            assertThat(suggestion.title).isEqualTo(title)
            assertThat(suggestion.url).isEqualTo(resolvedUrl)
            assertThat(suggestion.thumbnail).isEqualTo(thumbPath)
            assertThat(suggestion.preview).isEqualTo(screenPath)
            assertThat(suggestion.keywords).isEqualTo(keywords)
            assertThat(suggestion.tags).isEqualTo(tags)
            assertThat(suggestion.collections).isEqualTo(collections)

            coVerify(exactly = 1) { processorFactory.createProcessors(url, ExtractionPolicy.PARTIAL) }
            verify(exactly = 1) { processor.close() }

            coVerify(exactly = 1) { groupSetService.matchWithContent(content) }
            coVerify(exactly = 1) { processor.process(resourceSet) }
            coVerify(exactly = 1) { processor.linkContent }

            verify(exactly = 1) { resourceManager.saveTempFile(url, thumb.image, ResourceType.THUMBNAIL, thumb.extension) }
            verify(exactly = 1) { resourceManager.saveTempFile(url, screen.image, ResourceType.PREVIEW, screen.extension) }
        }

        @Test
        fun testSuggestCompletedExceptionally() = runBlocking(TestCoroutineContext()) {
            val url = "google.com"

            val processor = mockk<LinkProcessor>(relaxUnitFun = true)

            val exception = RuntimeException("error during computation")
            coEvery { processor.process(any()) } throws exception
            every { processor.close() } just Runs

            coEvery { processorFactory.createProcessors(url, ExtractionPolicy.PARTIAL) } returns listOf(processor)

            val deferred = CompletableDeferred<Suggestion>()
            val channel = worker.apply { runner = this@runBlocking.coroutineContext }.worker()
            channel.send(SuggestLinkProcessingRequest(url, deferred))
            channel.close()

            assertThat(deferred.getCompletionExceptionOrNull()?.message).isEqualTo(exception.message)

            coVerify(exactly = 1) { processorFactory.createProcessors(url, ExtractionPolicy.PARTIAL) }
            verify(exactly = 1) { processor.close() }

            coVerify(exactly = 1) { processor.process(any()) }
            verify(exactly = 0) { resourceManager.saveTempFile(any(), any(), any(), any()) }
        }
    }

    @Nested
    inner class ActiveCheck {
        @Test
        fun testDefaultActiveCheck() = runBlocking(TestCoroutineContext()) {
            val url = "google.com"

            val processor = mockk<LinkProcessor>(relaxUnitFun = true)
            every { processor.close() } just Runs

            coEvery { processorFactory.createProcessors(url, ExtractionPolicy.PARTIAL) } returns listOf(processor)

            val deferred = CompletableDeferred<Boolean>()
            val channel = worker.apply { runner = this@runBlocking.coroutineContext }.worker()
            channel.send(ActiveLinkCheckingRequest(url, deferred))
            channel.close()

            val active = deferred.await()
            assertThat(active).isTrue()

            coVerify(exactly = 1) { processorFactory.createProcessors(url, ExtractionPolicy.PARTIAL) }
            verify(exactly = 1) { processor.close() }
        }

        @Test
        fun testDefaultActiveCheckFoundDeadLink() = runBlocking(TestCoroutineContext()) {
            val url = "google.com"

            val processor = mockk<LinkProcessor>(relaxUnitFun = true)

            val exception = RuntimeException("error during computation")
            coEvery { processor.init() } throws exception
            every { processor.close() } just Runs

            coEvery { processorFactory.createProcessors(url, ExtractionPolicy.PARTIAL) } returns listOf(processor)

            val deferred = CompletableDeferred<Boolean>()
            val channel = worker.apply { runner = this@runBlocking.coroutineContext }.worker()
            channel.send(ActiveLinkCheckingRequest(url, deferred))
            channel.close()

            val active = deferred.await()
            assertThat(active).isFalse()

            coVerify(exactly = 1) { processorFactory.createProcessors(url, ExtractionPolicy.PARTIAL) }
            verify(exactly = 1) { processor.close() }
        }
    }
}
