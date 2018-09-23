package worker

import common.DatabaseTest
import common.Link
import common.TestCoroutineContext
import entry.LinkService
import io.mockk.*
import kotlinx.coroutines.experimental.runBlocking
import notify.NotifyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import resource.ResourceRetriever
import util.createDummyWorkerSchedule
import kotlin.coroutines.experimental.coroutineContext

class DiscussionFinderWorkerTest: DatabaseTest() {

    private val testUrl = "https://www.factorio.com/blog/post/fff-246"
    private val link = Link("id1", "title", testUrl, "factorio.com", "", 100)

    private val linkService = mockk<LinkService>()
    private val retriever = mockk<ResourceRetriever>()
    private val notifyService = mockk<NotifyService>(relaxUnitFun = true)
    private val linkSlot = slot<Link>()

    @BeforeEach
    fun setup() {
        every { linkService.get("id1") } answers { if(linkSlot.isCaptured) linkSlot.captured else link }
        every { linkService.update(capture(linkSlot)) } answers { link } andThen { linkSlot.captured }

        coEvery { notifyService.accept(any(), ofType(Link::class)) } just Runs
    }

    @Test
    fun testNoResponse(): Unit = runBlocking(TestCoroutineContext()) {
        coEvery { retriever.getString(match { it.contains("hn.algolia") }) } returns ""
        coEvery { retriever.getString(match { it.contains("reddit.com") }) } returns ""

        val worker = DiscussionFinderWorker(linkService, retriever, notifyService)
                .apply { runner = coroutineContext }.worker()

        worker.send(DiscussionFinderWorkerRequest(link.id))
        worker.close()

        verify(exactly = 5) { linkService.get(link.id) }
        assertThat(link.props.containsAttribute("discussions")).isFalse()

        coVerify(exactly = 5 * 2) { retriever.getString(any()) }
        coVerify(exactly = 0) { notifyService.accept(any(), any()) }
    }

    @Test
    fun testSameResponse(): Unit = runBlocking(TestCoroutineContext()) {
        coEvery { retriever.getString(match { it.contains("hn.algolia") }) } returns getFile("/hacker_discussions.json")
        coEvery { retriever.getString(match { it.contains("reddit.com") }) } returns getFile("/reddit_discussions.json")

        val worker = DiscussionFinderWorker(linkService, retriever, notifyService)
                .apply { runner = coroutineContext }.worker()

        worker.send(DiscussionFinderWorkerRequest(link.id))
        worker.close()

        verify(exactly = 5) { linkService.get(link.id) }
        verify(exactly = 5) { linkService.update(ofType(Link::class)) }
        assertThat(linkSlot.captured.props.containsAttribute("discussions")).isTrue()
        val discussions = linkSlot.captured.props.getAttribute("discussions") as List<Any?>
        assertThat(discussions).hasSize(6)

        assertThat(discussions).extracting("title")
                .containsExactlyInAnyOrder("Ok-Cancel versus Cancel-Ok", "r/techgeeks", "r/programming",
                        "r/hackernews", "r/bprogramming", "r/factorio")
        assertThat(discussions).extracting("url").doesNotHaveDuplicates()

        coVerify(exactly = 5 * 2) { retriever.getString(any()) }
        coVerify(exactly = 5) { notifyService.accept(any(), linkSlot.captured) }
    }

    @Test
    fun testInitFromSchedule() = runBlocking(TestCoroutineContext()) {
        coEvery { retriever.getString(match { it.contains("hn.algolia") }) } returns getFile("/hacker_discussions.json")
        coEvery { retriever.getString(match { it.contains("reddit.com") }) } returns getFile("/reddit_discussions.json")

        createDummyWorkerSchedule(DiscussionFinderWorker::class.java.simpleName, "key", DiscussionFinderWorkerRequest(link.id, 2))

        val worker = DiscussionFinderWorker(linkService, retriever, notifyService)
                .apply { runner = coroutineContext }.worker()

        worker.close()

        verify(exactly = 2) { linkService.get(link.id) }
        verify(exactly = 2) { linkService.update(ofType(Link::class)) }
        assertThat(linkSlot.captured.props.containsAttribute("discussions")).isTrue()
        assertThat(linkSlot.captured.props.getAttribute("discussions") as List<*>).hasSize(6)

        coVerify(exactly = 2 * 2) { retriever.getString(any()) }
        coVerify(exactly = 2) { notifyService.accept(any(), linkSlot.captured) }
    }

    @Test
    fun testDifferingResponses(): Unit = runBlocking(TestCoroutineContext()) {
        coEvery { retriever.getString(match { it.contains("hn.algolia") }) } returns "" andThen getFile("/hacker_discussions.json") andThen ""
        coEvery { retriever.getString(match { it.contains("reddit.com") }) } returns "" andThen getFile("/reddit_discussions.json") andThen ""

        val worker = DiscussionFinderWorker(linkService, retriever, notifyService)
                .apply { runner = coroutineContext }.worker()

        worker.send(DiscussionFinderWorkerRequest(link.id))
        worker.close()

        // ensure items not removed
        val discussions = linkSlot.captured.props.getAttribute("discussions") as List<Any?>
        assertThat(discussions).hasSize(6)
        assertThat(discussions).extracting("url").doesNotHaveDuplicates()

        coVerify(exactly = 5 * 2) { retriever.getString(any()) }
        coVerify(exactly = 1) { notifyService.accept(any(), linkSlot.captured) }
    }

    @Test
    fun testWorkerContinues() = runBlocking(TestCoroutineContext()){
        val hnResponses = listOf("", "", "", "", getFile("/hacker_discussions.json"))
        val redditResponses = listOf("", "", "", "", getFile("/reddit_discussions.json"))
        coEvery { retriever.getString(match { it.contains("hn.algolia") }) } returnsMany hnResponses
        coEvery { retriever.getString(match { it.contains("reddit.com") }) } returnsMany redditResponses

        val worker = DiscussionFinderWorker(linkService, retriever, notifyService)
                .apply { runner = coroutineContext }.worker()

        worker.send(DiscussionFinderWorkerRequest(link.id))
        worker.close()

        verify(exactly = 6) { linkService.get(link.id) }
        verify(exactly = 2) { linkService.update(ofType(Link::class)) }
        val discussions = linkSlot.captured.props.getAttribute("discussions") as List<Any?>
        assertThat(discussions).hasSize(6)

        coVerify(exactly = 6 * 2) { retriever.getString(any()) }
        coVerify(exactly = 2) { notifyService.accept(any(), linkSlot.captured) }
    }

    private fun getFile(name: String) = this.javaClass.getResource(name).readText()

}