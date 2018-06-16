package worker

import common.BaseProperties
import common.Link
import common.TestCoroutineContext
import entry.LinkService
import io.mockk.*
import kotlinx.coroutines.experimental.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import resource.ResourceRetriever
import schedule.ScheduleService
import schedule.ScheduleType
import schedule.ScheduledJob

class DiscussionFinderWorkerTest {

    private val testUrl = "https://www.factorio.com/blog/post/fff-246"
    private val link = Link("id1", "title", testUrl, "factorio.com", 100, emptyList(), BaseProperties())

    private val scheduleService = mockk<ScheduleService>()
    private val linkService = mockk<LinkService>()
    private val retriever = mockk<ResourceRetriever>()
    private val intervals = mutableListOf<ScheduledJob>()
    private val linkSlot = slot<Link>()

    @BeforeEach
    fun before() {
        every { scheduleService.get(ScheduleType.DISCUSSION_FINDER) } returns emptyList()
        every { scheduleService.add(any()) } just Runs
        every { scheduleService.update(capture(intervals)) } returns 1
        every { scheduleService.delete(ScheduledJob(link.id, ScheduleType.DISCUSSION_FINDER)) } returns true

        every { linkService.get("id1") } answers { link } andThen { linkSlot.captured }
        every { linkService.update(capture(linkSlot)) } answers { link } andThen { linkSlot.captured }
    }

    @Test
    fun testNoResponse(): Unit = runBlocking(TestCoroutineContext()) {
        every { retriever.getString(match { it.contains("hn.algolia") }) } returns ""
        every { retriever.getString(match { it.contains("reddit.com") }) } returns ""

        val worker = DiscussionFinderWorker(linkService, scheduleService, retriever)
                .apply { runner = coroutineContext }.worker()

        worker.send(link)
        worker.close()

        verify(exactly = 1) { scheduleService.get(ScheduleType.DISCUSSION_FINDER) }
        verify(exactly = 1) { scheduleService.add(any()) }
        verify(exactly = 1) { scheduleService.delete(ScheduledJob(link.id, ScheduleType.DISCUSSION_FINDER)) }
        verify(exactly = 4) { scheduleService.update(any()) }
        assertThat(intervals).hasSize(4).doesNotHaveDuplicates()

        verify(exactly = 5) { linkService.get(link.id) }
        verify(exactly = 5) { linkService.update(ofType(Link::class)) }
        assertThat(linkSlot.captured.props.containsAttribute("discussions")).isTrue()
        assertThat(linkSlot.captured.props.getAttribute("discussions") as List<*>).isEmpty()

        verify(exactly = 5 * 2) { retriever.getString(any()) }
    }

    @Test
    fun testSameResponse(): Unit = runBlocking(TestCoroutineContext()) {
        every { retriever.getString(match { it.contains("hn.algolia") }) } returns getFile("/hacker_discussions.json")
        every { retriever.getString(match { it.contains("reddit.com") }) } returns getFile("/reddit_discussions.json")

        val worker = DiscussionFinderWorker(linkService, scheduleService, retriever)
                .apply { runner = coroutineContext }.worker()

        worker.send(link)
        worker.close()

        verify(exactly = 1) { scheduleService.get(ScheduleType.DISCUSSION_FINDER) }
        verify(exactly = 1) { scheduleService.add(any()) }
        verify(exactly = 1) { scheduleService.delete(ScheduledJob(link.id, ScheduleType.DISCUSSION_FINDER)) }
        verify(exactly = 4) { scheduleService.update(any()) }
        assertThat(intervals).hasSize(4).doesNotHaveDuplicates()

        verify(exactly = 5) { linkService.get(link.id) }
        verify(exactly = 5) { linkService.update(ofType(Link::class)) }
        assertThat(linkSlot.captured.props.containsAttribute("discussions")).isTrue()
        val discussions = linkSlot.captured.props.getAttribute("discussions") as List<Any?>
        assertThat(discussions).hasSize(6)

        assertThat(discussions).extracting("title")
                .containsExactlyInAnyOrder("Ok-Cancel versus Cancel-Ok", "r/techgeeks", "r/programming",
                        "r/hackernews", "r/bprogramming", "r/factorio")
        assertThat(discussions).extracting("url").doesNotHaveDuplicates()

        verify(exactly = 5 * 2) { retriever.getString(any()) }
    }

    // test init from schedule table

    private fun getFile(name: String) = this.javaClass.getResource(name).readText()

}