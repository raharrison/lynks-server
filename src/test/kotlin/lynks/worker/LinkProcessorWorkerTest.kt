package lynks.worker

import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import lynks.common.DEAD_LINK_PROP
import lynks.common.Environment
import lynks.common.Link
import lynks.entry.EntryAuditService
import lynks.entry.LinkService
import lynks.group.Collection
import lynks.group.GroupSet
import lynks.group.GroupSetService
import lynks.group.Tag
import lynks.link.LinkDetails
import lynks.link.LinkProcessor
import lynks.link.LinkProcessorFactory
import lynks.link.SuggestResponse
import lynks.notify.NotifyService
import lynks.resource.*
import lynks.suggest.Suggestion
import lynks.util.FileUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

@ExperimentalCoroutinesApi
class LinkProcessorWorkerTest {

    private val readableTextContentPath = Paths.get(Environment.resource.resourceTempPath, "readable_text.txt")
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
        if(Files.exists(readableTextContentPath)) {
            Files.delete(readableTextContentPath)
        }
    }

    @Nested
    inner class Persist {
        @Test
        fun testDefaultPersistAllTypes() = runTest {
            val link = Link("id1", "title", "google.com", "google.com", "", 100, 100)
            val resourceSet = ResourceType.linkBaseline()
            val content = "article content"
            val generatedResources =listOf(
                GeneratedResource(ResourceType.SCREENSHOT, "screenshotPath", PNG),
                GeneratedResource(ResourceType.THUMBNAIL, "thumbPath", JPG),
                GeneratedResource(ResourceType.PREVIEW, "previewPath", JPG),
                GeneratedResource(ResourceType.DOCUMENT, "docPath", PDF),
                GeneratedResource(ResourceType.PAGE, "screenshotPath", HTML),
                GeneratedResource(ResourceType.READABLE_TEXT, readableTextContentPath.toString(), TEXT),
            )
            FileUtils.writeToFile(readableTextContentPath, content.toByteArray())
            val processor = mockk<LinkProcessor>(relaxUnitFun = true)

            coEvery { notifyService.accept(any(), ofType(Link::class)) } just Runs
            coEvery { processor.scrapeResources(resourceSet) } returns generatedResources
            every { resourceManager.migrateGeneratedResources(link.id, any()) } returns listOf(
                Resource("rid1", link.id, "screenshot", PNG, ResourceType.SCREENSHOT, 1189, 100, 100),
                Resource("rid2", link.id, "thumbnail", JPG, ResourceType.THUMBNAIL, 456, 100, 100),
                Resource("rid3", link.id, "preview", JPG, ResourceType.PREVIEW, 743, 100, 100)
            )
            coEvery { processor.enrich(link.props) } just Runs
            every { processor.close() } just Runs

            coEvery { processorFactory.createProcessors(link.url) } returns listOf(processor)
            every { resourceManager.deleteTempFiles(link.url) } just Runs
            link.thumbnailId = "rid2"
            every { linkService.update(link) } returns link
            every { linkService.mergeProps(eq("id1"), any()) } just Runs

            val channel = worker.apply { runner = this@runTest.coroutineContext }.worker()
            channel.send(PersistLinkProcessingRequest(link, resourceSet, true))
            advanceUntilIdle()
            channel.close()

            coVerify(exactly = 1) { processorFactory.createProcessors(link.url) }
            verify(exactly = 1) { processor.close() }
            verify(exactly = 1) { linkService.mergeProps(eq("id1"), any()) }
            verify(exactly = 1) { linkService.update(link) }
            coVerify(exactly = 1) { notifyService.accept(any(), ofType(Link::class)) }
            assertThat(link.content).isEqualTo("article content")

            coVerify(exactly = 1) { processor.scrapeResources(resourceSet) }
            verify(exactly = 1) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
        }

        @Test
        fun testDefaultPersistSingleType() = runTest {
            val link = Link("id1", "title", "google.com", "google.com", "", 100, 100)
            val resourceSet = EnumSet.of(ResourceType.SCREENSHOT)

            val generatedResources =listOf(
                GeneratedResource(ResourceType.SCREENSHOT, "screenshotPath", PNG)
            )
            val processor = mockk<LinkProcessor>(relaxUnitFun = true)

            coEvery { notifyService.accept(any(), ofType(Link::class)) } just Runs
            coEvery { processor.scrapeResources(resourceSet) } returns generatedResources
            every { resourceManager.migrateGeneratedResources(link.id, generatedResources) } returns emptyList()
            coEvery { processor.enrich(link.props) } just Runs
            every { processor.close() } just Runs

            coEvery { processorFactory.createProcessors(link.url) } returns listOf(processor)
            every { resourceManager.deleteTempFiles(link.url) } just Runs
            every { linkService.update(link) } returns link
            every { linkService.mergeProps(eq("id1"), any()) } just Runs

            val channel = worker.apply { runner = this@runTest.coroutineContext }.worker()
            channel.send(PersistLinkProcessingRequest(link, resourceSet, true))
            advanceUntilIdle()
            channel.close()

            coVerify(exactly = 1) { processorFactory.createProcessors(link.url) }
            verify(exactly = 1) { processor.close() }
            verify(exactly = 1) { linkService.mergeProps(eq("id1"), any()) }
            verify(exactly = 1) { linkService.update(link) }
            coVerify(exactly = 1) { notifyService.accept(any(), ofType(Link::class)) }
            coVerify(exactly = 1) { processor.scrapeResources(resourceSet) }
            verify(exactly = 1) { resourceManager.migrateGeneratedResources(link.id, generatedResources) }
            verify(exactly = 1) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
        }

        @Test
        fun testDefaultPersistNoProcessFlag() = runTest {
            val link = Link("id1", "title", "google.com", "google.com", "", 100, 100)

            val processor = mockk<LinkProcessor>(relaxUnitFun = true)
            coEvery { notifyService.accept(any(), ofType(Link::class)) } just Runs
            coEvery { processor.enrich(link.props) } just Runs
            every { processor.close() } just Runs

            coEvery { processorFactory.createProcessors(link.url) } returns listOf(processor)
            every { linkService.update(link) } returns link
            every { linkService.mergeProps(eq("id1"), any()) } just Runs
            every { resourceManager.deleteTempFiles(link.url) } just Runs

            val channel = worker.apply { runner = this@runTest.coroutineContext }.worker()
            channel.send(PersistLinkProcessingRequest(link, ResourceType.linkBaseline(), false))
            advanceUntilIdle()
            channel.close()

            coVerify(exactly = 1) { processorFactory.createProcessors(link.url) }
            verify(exactly = 1) { processor.close() }
            verify(exactly = 1) { linkService.mergeProps(eq("id1"), any()) }
            verify(exactly = 1) { linkService.update(link) }
            coVerify(exactly = 0) { notifyService.accept(any(), ofType(Link::class)) }

            coVerify(exactly = 0) { processor.scrapeResources(any()) }
            verify(exactly = 0) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
        }

        @Test
        fun testDefaultPersistNoResourceTypes() = runTest {
            val link = Link("id1", "title", "google.com", "google.com", "", 100, 100)

            val processor = mockk<LinkProcessor>(relaxUnitFun = true)

            coEvery { processorFactory.createProcessors(link.url) } returns listOf(processor)
            every { linkService.update(link) } returns link
            every { linkService.mergeProps(eq("id1"), any()) } just Runs
            every { resourceManager.deleteTempFiles(link.url) } just Runs

            val channel = worker.apply { runner = this@runTest.coroutineContext }.worker()
            channel.send(PersistLinkProcessingRequest(link, EnumSet.noneOf(ResourceType::class.java), true))
            advanceUntilIdle()
            channel.close()

            coVerify(exactly = 1) { processorFactory.createProcessors(link.url) }
            verify(exactly = 1) { processor.close() }
            verify(exactly = 1) { linkService.mergeProps(eq("id1"), any()) }
            verify(exactly = 1) { linkService.update(link) }
            coVerify(exactly = 1) { notifyService.accept(any(), ofType(Link::class)) }

            coVerify(exactly = 0) { processor.scrapeResources(any()) }
            verify(exactly = 1) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
        }

        @Test
        fun testDefaultPersistCompletedExceptionally() = runTest {
            val link = Link("id1", "title", "google.com", "google.com", "", 100, 100)

            val exception = RuntimeException("error during computation")
            val processor = mockk<LinkProcessor>(relaxUnitFun = true)
            coEvery { notifyService.accept(any(), null) } just Runs
            coEvery { processor.scrapeResources(any()) } throws exception
            every { processor.close() } just Runs

            coEvery { processorFactory.createProcessors(link.url) } returns listOf(processor)
            every { linkService.update(link) } returns link
            every { linkService.mergeProps(eq("id1"), any()) } just Runs
            every { resourceManager.deleteTempFiles(link.url) } just Runs

            val channel = worker.apply { runner = this@runTest.coroutineContext }.worker()
            channel.send(PersistLinkProcessingRequest(link, ResourceType.linkBaseline(), true))
            advanceUntilIdle()
            channel.close()

            coVerify(exactly = 1) { processorFactory.createProcessors(link.url) }
            verify(exactly = 1) { processor.close() }
            verify(exactly = 0) { linkService.update(link) }
            verify(exactly = 1) { linkService.mergeProps(eq("id1"), any()) }
            coVerify(exactly = 1) { notifyService.accept(any(), null) }
            verify(exactly = 1) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
            assertThat(link.props.containsAttribute(DEAD_LINK_PROP)).isTrue()

            Unit
        }
    }

    @Nested
    inner class Suggest {
        @Test
        fun testDefaultSuggest() = runTest {
            val url = "google.com"
            val resourceSet = EnumSet.of(ResourceType.PREVIEW, ResourceType.THUMBNAIL, ResourceType.READABLE_TEXT)
            val processor = mockk<LinkProcessor>(relaxUnitFun = true)

            val content = "article content"
            val title = "title"
            val keywords = setOf("search", "other", "important")
            val tags = listOf(
                Tag("t1", "tag1", null, 124L, 1234L),
                Tag("t2", "tag2", null, 124L, 1234L)
            )
            val collections = listOf(
                Collection("c1", "col1", null, mutableSetOf(), 124L, 1234L),
                Collection("c2", "col2", null, mutableSetOf(), 124L, 1234L)
            )
            FileUtils.writeToFile(readableTextContentPath, content.toByteArray())
            val linkDetails = LinkDetails(url, title, keywords, "description")
            val resources = listOf(GeneratedResource(ResourceType.PREVIEW, "previewPath", JPG),
                GeneratedResource(ResourceType.THUMBNAIL, "thumbPath", JPG),
                GeneratedResource(ResourceType.READABLE_TEXT, readableTextContentPath.toString(), TEXT))
            val suggestResponse = SuggestResponse(linkDetails, resources)

            coEvery { processor.suggest(resourceSet) } returns suggestResponse
            every { processor.close() } just Runs
            every { resourceManager.constructTempUrlFromPath("thumbPath") } returns "thumbPath"
            every { resourceManager.constructTempUrlFromPath("previewPath") } returns "previewPath"
            every { groupSetService.matchWithContent(content) } returns GroupSet(tags, collections)

            coEvery { processorFactory.createProcessors(url) } returns listOf(processor)

            val deferred = CompletableDeferred<Suggestion>()
            val channel = worker.apply { runner = this@runTest.coroutineContext }.worker()
            channel.send(SuggestLinkProcessingRequest(url, deferred))
            advanceUntilIdle()
            channel.close()

            val suggestion = deferred.await()
            assertThat(suggestion.title).isEqualTo(title)
            assertThat(suggestion.url).isEqualTo(url)
            assertThat(suggestion.thumbnail).isEqualTo("thumbPath")
            assertThat(suggestion.preview).isEqualTo("previewPath")
            assertThat(suggestion.keywords).isEqualTo(keywords)
            assertThat(suggestion.tags).isEqualTo(tags)
            assertThat(suggestion.collections).isEqualTo(collections)

            coVerify(exactly = 1) { processorFactory.createProcessors(url) }
            verify(exactly = 1) { processor.close() }

            coVerify(exactly = 1) { groupSetService.matchWithContent(content) }
            coVerify(exactly = 1) { processor.suggest(resourceSet) }
        }

        @Test
        fun testSuggestCompletedExceptionally() = runTest {
            val url = "google.com"

            val processor = mockk<LinkProcessor>(relaxUnitFun = true)

            val exception = RuntimeException("error during computation")
            coEvery { processor.suggest(any()) } throws exception
            every { processor.close() } just Runs

            coEvery { processorFactory.createProcessors(url) } returns listOf(processor)

            val deferred = CompletableDeferred<Suggestion>()
            val channel = worker.apply { runner = this@runTest.coroutineContext }.worker()
            channel.send(SuggestLinkProcessingRequest(url, deferred))
            advanceUntilIdle()
            channel.close()

            assertThat(deferred.getCompletionExceptionOrNull()?.message).isEqualTo(exception.message)

            coVerify(exactly = 1) { processorFactory.createProcessors(url) }
            verify(exactly = 1) { processor.close() }
            coVerify(exactly = 1) { processor.suggest(any()) }
        }
    }

    @Nested
    inner class ActiveCheck {
        @Test
        fun testDefaultActiveCheck() = runTest {
            val url = "google.com"

            val processor = mockk<LinkProcessor>(relaxUnitFun = true)
            every { processor.close() } just Runs

            coEvery { processorFactory.createProcessors(url) } returns listOf(processor)

            val deferred = CompletableDeferred<Boolean>()
            val channel = worker.apply { runner = this@runTest.coroutineContext }.worker()
            channel.send(ActiveLinkCheckingRequest(url, deferred))
            advanceUntilIdle()
            channel.close()

            val active = deferred.await()
            assertThat(active).isTrue()

            coVerify(exactly = 1) { processorFactory.createProcessors(url) }
            verify(exactly = 1) { processor.close() }
        }

        @Test
        fun testDefaultActiveCheckFoundDeadLink() = runTest {
            val url = "google.com"

            val processor = mockk<LinkProcessor>(relaxUnitFun = true)

            val exception = RuntimeException("error during computation")
            coEvery { processor.init() } throws exception
            every { processor.close() } just Runs

            coEvery { processorFactory.createProcessors(url) } returns listOf(processor)

            val deferred = CompletableDeferred<Boolean>()
            val channel = worker.apply { runner = this@runTest.coroutineContext }.worker()
            channel.send(ActiveLinkCheckingRequest(url, deferred))
            advanceUntilIdle()
            channel.close()

            val active = deferred.await()
            assertThat(active).isFalse()

            coVerify(exactly = 1) { processorFactory.createProcessors(url) }
            verify(exactly = 1) { processor.close() }
        }
    }
}
