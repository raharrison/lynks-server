package task

import common.BaseProperties
import common.Link
import entry.LinkService
import io.mockk.*
import kotlinx.coroutines.experimental.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import worker.PersistLinkProcessingRequest
import worker.WorkerRegistry

class LinkProcessingTaskTest {

    private val workerRegistry = mockk<WorkerRegistry>()
    private val linkService = mockk<LinkService>()

    private val linkProcessingTask = LinkProcessingTask("tid", "eid").also {
        it.workerRegistry = workerRegistry
        it.linkService = linkService
    }

    @Test
    fun testContextConstruct() {
        val context = linkProcessingTask.createContext(emptyMap())
        assertThat(context.input).isEmpty()
        assertThat(context).isOfAnyClassIn(TaskContext::class.java)
    }

    @Test
    fun testBuilder() {
        val builder = LinkProcessingTask.build()
        assertThat(builder.clazz).isEqualTo(LinkProcessingTask::class)
        assertThat(builder.context.input).isEmpty()
    }

    @Test
    fun testProcess() {
        val context = linkProcessingTask.createContext(emptyMap())
        val link = Link("eid", "title", "url", "", "", 1, emptyList(), BaseProperties())

        every { linkService.get("eid") } returns link
        every { workerRegistry.acceptLinkWork(any()) } just Runs

        runBlocking {
            linkProcessingTask.process(context)
        }

        verify(exactly = 1) { linkService.get("eid") }
        verify(exactly = 1) { workerRegistry.acceptLinkWork(match {
            it is PersistLinkProcessingRequest && it.link == link
        }) }
    }

    @Test
    fun testProcessNoResult() {
        val context = linkProcessingTask.createContext(emptyMap())

        every { linkService.get("eid") } returns null

        runBlocking {
            linkProcessingTask.process(context)
        }

        verify(exactly = 1) { linkService.get("eid") }
    }

}