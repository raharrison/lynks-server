package lynks.worker

import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import lynks.common.BaseProperties
import lynks.common.DISCUSSIONS_PROP
import lynks.common.DatabaseTest
import lynks.common.Link
import lynks.entry.EntryAuditService
import lynks.entry.LinkService
import lynks.notify.NotifyService
import lynks.resource.ResourceRetriever
import lynks.util.createDummyWorkerSchedule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class DiscussionFinderWorkerTest: DatabaseTest() {

    private val testUrl = "https://www.factorio.com/blog/post/fff-246"
    private val link = Link("id1", "title", testUrl, "factorio.com", "", 100, 100)

    private val linkService = mockk<LinkService>()
    private val retriever = mockk<ResourceRetriever>()
    private val notifyService = mockk<NotifyService>(relaxUnitFun = true)
    private val entryAuditService = mockk<EntryAuditService>(relaxUnitFun = true)
    private val propsSlot = slot<BaseProperties>()

    @BeforeEach
    fun setup() {
        every { linkService.get("id1") } returns link
        every { linkService.mergeProps(eq(link.id), capture(propsSlot)) } just Runs

        coEvery { notifyService.accept(any(), ofType(Link::class)) } just Runs
    }

    @Test
    fun testNoResponse(): Unit = runTest {
        coEvery { retriever.getString(match { it.contains("hn.algolia") }) } returns ""
        coEvery { retriever.getString(match { it.contains("reddit.com") }) } returns ""

        val worker = DiscussionFinderWorker(linkService, retriever, notifyService, entryAuditService)
                .apply { runner = this@runTest.coroutineContext }.worker()

        worker.send(DiscussionFinderWorkerRequest(link.id))
        advanceUntilIdle()

        assertThat(link.props.containsAttribute(DISCUSSIONS_PROP)).isFalse()

        coVerify(exactly = 5) { linkService.get(link.id) }
        coVerify(exactly = 5 * 2) { retriever.getString(any()) }
        coVerify(exactly = 0) { notifyService.accept(any(), any()) }
        coVerify(exactly = 5) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
        worker.close()
    }

    @Test
    fun testSameResponse(): Unit = runTest {
        coEvery { retriever.getString(match { it.contains("hn.algolia") }) } returns getFile("/hacker_discussions.json")
        coEvery { retriever.getString(match { it.contains("reddit.com") }) } returns getFile("/reddit_discussions.json")

        val worker = DiscussionFinderWorker(linkService, retriever, notifyService, entryAuditService)
                .apply { runner = this@runTest.coroutineContext }.worker()

        worker.send(DiscussionFinderWorkerRequest(link.id))
        advanceUntilIdle()

        verify(exactly = 5) { linkService.get(link.id) }
        verify(exactly = 5) { linkService.mergeProps(eq(link.id), ofType(BaseProperties::class)) }
        assertThat(propsSlot.captured.containsAttribute(DISCUSSIONS_PROP)).isTrue()
        val discussions = propsSlot.captured.getAttribute(DISCUSSIONS_PROP) as List<Any?>
        assertThat(discussions).hasSize(6)

        assertThat(discussions).extracting("title")
                .containsExactlyInAnyOrder("Ok-Cancel versus Cancel-Ok", "/r/techgeeks", "/r/programming",
                        "/r/hackernews", "/r/bprogramming", "/r/factorio")
        assertThat(discussions).extracting("url").doesNotHaveDuplicates()

        coVerify(exactly = 5 * 2) { retriever.getString(any()) }
        coVerify(exactly = 1) { notifyService.accept(any(), link) }
        coVerify(exactly = 1) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
        worker.close()
    }

    @Test
    fun testRedditCrosspostDiscussions() = runTest {
        coEvery { retriever.getString(match { it.contains("hn.algolia") }) } returns ""
        coEvery { retriever.getString(match { it.contains("reddit.com") }) } returns getFile("/reddit_crosspost_discussions.json")

        val url = "https://old.reddit.com/r/programming/comments/ftkiyp/how_we_reduced_our_google_maps_api_cost_by_94/"
        val link = Link("id1", "title", url, "reddit.com", "", 100, 100)

        every { linkService.get(link.id) } returns link
        every { linkService.mergeProps(eq(link.id), capture(propsSlot)) } just Runs

        val worker = DiscussionFinderWorker(linkService, retriever, notifyService, entryAuditService)
            .apply { runner = this@runTest.coroutineContext }.worker()

        worker.send(DiscussionFinderWorkerRequest(link.id))
        advanceUntilIdle()

        verify(exactly = 5) { linkService.mergeProps(eq(link.id), ofType(BaseProperties::class)) }
        assertThat(propsSlot.captured.containsAttribute(DISCUSSIONS_PROP)).isTrue()
        val discussions = propsSlot.captured.getAttribute(DISCUSSIONS_PROP) as List<Any?>
        assertThat(discussions).hasSize(4)

        assertThat(discussions).extracting("title")
            .containsExactlyInAnyOrder("/r/GoogleMaps", "/r/hackernews", "/r/mistyfront", "/r/patient_hackernews")
        assertThat(discussions).extracting("url").doesNotHaveDuplicates()

        coVerify(exactly = 5 * 2) { retriever.getString(any()) }
        coVerify(exactly = 1) { notifyService.accept(any(), link) }
        coVerify(exactly = 1) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
        worker.close()
    }

    @Test
    fun testInitFromSchedule() = runTest {
        coEvery { retriever.getString(match { it.contains("hn.algolia") }) } returns getFile("/hacker_discussions.json")
        coEvery { retriever.getString(match { it.contains("reddit.com") }) } returns getFile("/reddit_discussions.json")

        createDummyWorkerSchedule(DiscussionFinderWorker::class.java.simpleName, "key", DiscussionFinderWorkerRequest(link.id, 2))

        val worker = DiscussionFinderWorker(linkService, retriever, notifyService, entryAuditService)
                .apply { runner = this@runTest.coroutineContext }.worker()
        advanceUntilIdle()

        verify(exactly = 2) { linkService.get(link.id) }
        verify(exactly = 2) { linkService.mergeProps(eq(link.id), ofType(BaseProperties::class)) }
        assertThat(propsSlot.captured.containsAttribute(DISCUSSIONS_PROP)).isTrue()
        assertThat(propsSlot.captured.getAttribute(DISCUSSIONS_PROP) as List<*>).hasSize(6)

        coVerify(exactly = 2 * 2) { retriever.getString(any()) }
        coVerify(exactly = 1) { notifyService.accept(any(), link) }
        coVerify(exactly = 1) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
        worker.close()
    }

    @Test
    fun testInitFromScheduleWithLastRunDelay() = runTest {
        coEvery { retriever.getString(match { it.contains("hn.algolia") }) } returns getFile("/hacker_discussions.json")
        coEvery { retriever.getString(match { it.contains("reddit.com") }) } returns getFile("/reddit_discussions.json")

        val lastRun = System.currentTimeMillis() - (30 * 60 * 1000) // 30 mins ago
        createDummyWorkerSchedule(DiscussionFinderWorker::class.java.simpleName, "key", DiscussionFinderWorkerRequest(link.id, 2), lastRun)

        val worker = DiscussionFinderWorker(linkService, retriever, notifyService, entryAuditService)
            .apply { runner = this@runTest.coroutineContext }.worker()
        advanceUntilIdle()

        verify(exactly = 2) { linkService.get(link.id) }
        verify(exactly = 2) { linkService.mergeProps(eq(link.id), ofType(BaseProperties::class)) }
        assertThat(propsSlot.captured.containsAttribute(DISCUSSIONS_PROP)).isTrue()
        assertThat(propsSlot.captured.getAttribute(DISCUSSIONS_PROP) as List<*>).hasSize(6)

        coVerify(exactly = 2 * 2) { retriever.getString(any()) }
        coVerify(exactly = 1) { notifyService.accept(any(), link) }
        coVerify(exactly = 1) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
        worker.close()
    }

    @Test
    fun testDifferingResponses(): Unit = runTest {
        coEvery { retriever.getString(match { it.contains("hn.algolia") }) } returns "" andThen getFile("/hacker_discussions.json") andThen ""
        coEvery { retriever.getString(match { it.contains("reddit.com") }) } returns "" andThen getFile("/reddit_discussions.json") andThen ""

        val worker = DiscussionFinderWorker(linkService, retriever, notifyService, entryAuditService)
                .apply { runner = this@runTest.coroutineContext }.worker()

        worker.send(DiscussionFinderWorkerRequest(link.id))
        advanceUntilIdle()

        // ensure items not removed
        val discussions = propsSlot.captured.getAttribute(DISCUSSIONS_PROP) as List<Any?>
        assertThat(discussions).hasSize(6)
        assertThat(discussions).extracting("url").doesNotHaveDuplicates()

        coVerify(exactly = 5 * 2) { retriever.getString(any()) }
        coVerify(exactly = 1) { notifyService.accept(any(), link) }
        coVerify(exactly = 5) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
        worker.close()
    }

    @Test
    fun testWorkerContinues() = runTest {
        val hnResponses = listOf("", "", "", "", getFile("/hacker_discussions.json"))
        val redditResponses = listOf("", "", "", "", getFile("/reddit_discussions.json"))
        coEvery { retriever.getString(match { it.contains("hn.algolia") }) } returnsMany hnResponses
        coEvery { retriever.getString(match { it.contains("reddit.com") }) } returnsMany redditResponses

        val worker = DiscussionFinderWorker(linkService, retriever, notifyService, entryAuditService)
                .apply { runner = this@runTest.coroutineContext }.worker()

        worker.send(DiscussionFinderWorkerRequest(link.id))
        advanceUntilIdle()

        verify(exactly = 6) { linkService.get(link.id) }
        verify(exactly = 2) { linkService.mergeProps(eq(link.id), ofType(BaseProperties::class)) }
        val discussions = propsSlot.captured.getAttribute(DISCUSSIONS_PROP) as List<Any?>
        assertThat(discussions).hasSize(6)

        coVerify(exactly = 6 * 2) { retriever.getString(any()) }
        coVerify(exactly = 1) { notifyService.accept(any(), link) }
        coVerify(exactly = 5) { entryAuditService.acceptAuditEvent(link.id, any(), any()) }
        worker.close()
    }

    private fun getFile(name: String) = this.javaClass.getResource(name).readText()

}
