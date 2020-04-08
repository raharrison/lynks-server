package task

import common.BaseProperties
import common.Link
import common.exception.ExecutionException
import entry.EntryAuditService
import entry.LinkService
import io.mockk.*
import kotlinx.coroutines.runBlocking
import link.summary.Summary
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import resource.ResourceRetriever
import util.Result

class LinkSummarizerTaskTest {

    private val resourceRetriever = mockk<ResourceRetriever>()
    private val linkService = mockk<LinkService>()
    private val entryAuditService = mockk<EntryAuditService>(relaxUnitFun = true)
    private val propsSlot = slot<BaseProperties>()

    private val linkSummarizerTask = LinkSummarizerTask("tid", "eid").also {
        it.resourceRetriever = resourceRetriever
        it.linkService = linkService
        it.entryAuditService = entryAuditService
    }

    @Test
    fun testContextConstruct() {
        val context = linkSummarizerTask.createContext(emptyMap())
        assertThat(context.input).isEmpty()
        assertThat(context).isOfAnyClassIn(TaskContext::class.java)
    }

    @Test
    fun testBuilder() {
        val builder = LinkSummarizerTask.build()
        assertThat(builder.clazz).isEqualTo(LinkSummarizerTask::class)
        assertThat(builder.context.input).isEmpty()
    }

    @Test
    fun testProcessGeneratesSummary() {
        val context = linkSummarizerTask.createContext(emptyMap())
        val link = Link("eid", "title", "url", "", "", 1, 1)

        every { linkService.get("eid") } returns link
        every { linkService.mergeProps("eid", any()) } just Runs
        val successResponse = this.javaClass.getResource("/smmry_response.json").readText()
        coEvery { resourceRetriever.getStringResult(any()) } returns Result.Success(successResponse)

        runBlocking {
            linkSummarizerTask.process(context)
        }

        verify(exactly = 1) { linkService.get("eid") }
        verify(exactly = 1) { linkService.mergeProps("eid", capture(propsSlot)) }
        verify(exactly = 1) { entryAuditService.acceptAuditEvent("eid", any(), any()) }

        assertThat(propsSlot.captured.getAttribute("summary")).isNotNull()
        assertThat(propsSlot.captured.getAttribute("summary")).isInstanceOf(Summary::class.java)
    }

    @Test
    fun testSummaryGenerationReturnsError() {
        val context = linkSummarizerTask.createContext(emptyMap())
        val link = Link("eid", "title", "url", "", "", 1, 1)

        every { linkService.get("eid") } returns link
        coEvery { resourceRetriever.getStringResult(any()) } returns Result.Failure(ExecutionException("error"))

        runBlocking {
            linkSummarizerTask.process(context)
        }

        verify(exactly = 1) { linkService.get("eid") }
        verify(exactly = 0) { linkService.mergeProps("eid", any()) }
        verify(exactly = 1) { entryAuditService.acceptAuditEvent("eid", any(), any()) }
    }

    @Test
    fun testProcessLinkDoesntExist() {
        val context = linkSummarizerTask.createContext(emptyMap())

        every { linkService.get("eid") } returns null

        runBlocking {
            linkSummarizerTask.process(context)
        }

        verify(exactly = 1) { linkService.get("eid") }
    }

}