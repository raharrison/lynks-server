package lynks.task.link

import io.mockk.*
import kotlinx.coroutines.runBlocking
import lynks.common.BaseProperties
import lynks.common.EntryId
import lynks.common.Link
import lynks.common.TaskId
import lynks.common.exception.ExecutionException
import lynks.entry.EntryAuditService
import lynks.entry.LinkService
import lynks.link.summary.Summary
import lynks.resource.ResourceRetriever
import lynks.task.TaskContext
import lynks.util.Result
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class LinkSummarizerTaskTest {

    private val resourceRetriever = mockk<ResourceRetriever>()
    private val linkService = mockk<LinkService>()
    private val entryAuditService = mockk<EntryAuditService>(relaxUnitFun = true)
    private val propsSlot = slot<BaseProperties>()

    private val linkSummarizerTask = LinkSummarizerTask(TaskId("tid"), EntryId("eid")).also {
        it.resourceRetriever = resourceRetriever
        it.linkService = linkService
        it.entryAuditService = entryAuditService
    }

    @Test
    fun testContextConstruct() {
        val context = linkSummarizerTask.createContext(emptyMap())
        assertThat(context).isOfAnyClassIn(TaskContext::class.java)
    }

    @Test
    fun testBuilder() {
        val builder = LinkSummarizerTask.build()
        assertThat(builder.clazz).isEqualTo(LinkSummarizerTask::class)
        assertThat(builder.params).isEmpty()
    }

    @Test
    fun testProcessGeneratesSummary() {
        val context = linkSummarizerTask.createContext(emptyMap())
        val link = Link(EntryId("eid"), "title", "url", "", "", Instant.EPOCH, Instant.EPOCH)

        every { linkService.get(EntryId("eid")) } returns link
        every { linkService.mergeProps(EntryId("eid"), any()) } just Runs
        val successResponse = this.javaClass.getResource("/smmry_response.json").readText()
        coEvery { resourceRetriever.getStringResult(any()) } returns Result.Success(successResponse)

        runBlocking {
            linkSummarizerTask.process(context)
        }

        verify(exactly = 1) { linkService.get(EntryId("eid")) }
        verify(exactly = 1) { linkService.mergeProps(EntryId("eid"), capture(propsSlot)) }
        verify(exactly = 1) { entryAuditService.acceptAuditEvent(EntryId("eid"), any(), any()) }

        assertThat(propsSlot.captured.getAttribute("summary")).isNotNull()
        assertThat(propsSlot.captured.getAttribute("summary")).isInstanceOf(Summary::class.java)
    }

    @Test
    fun testSummaryGenerationReturnsError() {
        val context = linkSummarizerTask.createContext(emptyMap())
        val link = Link(EntryId("eid"), "title", "url", "", "", Instant.EPOCH, Instant.EPOCH)

        every { linkService.get(EntryId("eid")) } returns link
        coEvery { resourceRetriever.getStringResult(any()) } returns Result.Failure(ExecutionException("error"))

        runBlocking {
            linkSummarizerTask.process(context)
        }

        verify(exactly = 1) { linkService.get(EntryId("eid")) }
        verify(exactly = 0) { linkService.mergeProps(EntryId("eid"), any()) }
        verify(exactly = 1) { entryAuditService.acceptAuditEvent(EntryId("eid"), any(), any()) }
    }

    @Test
    fun testProcessLinkDoesntExist() {
        val context = linkSummarizerTask.createContext(emptyMap())

        every { linkService.get(EntryId("eid")) } returns null

        runBlocking {
            linkSummarizerTask.process(context)
        }

        verify(exactly = 1) { linkService.get(EntryId("eid")) }
    }

}
