package task

import common.Link
import entry.LinkService
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import worker.WorkerRegistry

class DiscussionFinderTaskTest {

    private val workerRegistry = mockk<WorkerRegistry>()
    private val linkService = mockk<LinkService>()

    private val discussionFinderTask = DiscussionFinderTask("tid", "eid").also {
        it.workerRegistry = workerRegistry
        it.linkService = linkService
    }

    @Test
    fun testContextConstruct() {
        val context = discussionFinderTask.createContext(emptyMap())
        assertThat(context.input).isEmpty()
        assertThat(context).isOfAnyClassIn(TaskContext::class.java)
    }

    @Test
    fun testBuilder() {
        val builder = DiscussionFinderTask.build()
        assertThat(builder.clazz).isEqualTo(DiscussionFinderTask::class)
        assertThat(builder.context.input).isEmpty()
    }

    @Test
    fun testProcess() {
        val context = discussionFinderTask.createContext(emptyMap())
        val link = Link("eid", "title", "url", "", "", 1, 1)

        every { linkService.get("eid") } returns link
        every { workerRegistry.acceptDiscussionWork(any()) } just Runs

        runBlocking {
            discussionFinderTask.process(context)
        }

        verify(exactly = 1) { linkService.get("eid") }
        verify(exactly = 1) { workerRegistry.acceptDiscussionWork(link.id) }
    }

    @Test
    fun testProcessNoResult() {
        val context = discussionFinderTask.createContext(emptyMap())

        every { linkService.get("eid") } returns null

        runBlocking {
            discussionFinderTask.process(context)
        }

        verify(exactly = 1) { linkService.get("eid") }
    }

}