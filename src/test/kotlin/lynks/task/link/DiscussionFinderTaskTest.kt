package lynks.task.link

import io.mockk.*
import kotlinx.coroutines.runBlocking
import lynks.common.EntryId
import lynks.common.Link
import lynks.common.TaskId
import lynks.entry.LinkService
import lynks.task.TaskContext
import lynks.worker.WorkerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DiscussionFinderTaskTest {

    private val workerRegistry = mockk<WorkerRegistry>()
    private val linkService = mockk<LinkService>()

    private val discussionFinderTask = DiscussionFinderTask(TaskId("tid"), EntryId("eid")).also {
        it.workerRegistry = workerRegistry
        it.linkService = linkService
    }

    @Test
    fun testContextConstruct() {
        val context = discussionFinderTask.createContext(emptyMap())
        assertThat(context).isOfAnyClassIn(TaskContext::class.java)
    }

    @Test
    fun testBuilder() {
        val builder = DiscussionFinderTask.build()
        assertThat(builder.clazz).isEqualTo(DiscussionFinderTask::class)
        assertThat(builder.params).isEmpty()
    }

    @Test
    fun testProcess() {
        val context = discussionFinderTask.createContext(emptyMap())
        val link = Link(EntryId("eid"), "title", "url", "", "", 1, 1)

        every { linkService.get(EntryId("eid")) } returns link
        every { workerRegistry.acceptDiscussionWork(any()) } just Runs

        runBlocking {
            discussionFinderTask.process(context)
        }

        verify(exactly = 1) { linkService.get(EntryId("eid")) }
        verify(exactly = 1) { workerRegistry.acceptDiscussionWork(link.id) }
    }

    @Test
    fun testProcessNoResult() {
        val context = discussionFinderTask.createContext(emptyMap())

        every { linkService.get(EntryId("eid")) } returns null

        runBlocking {
            discussionFinderTask.process(context)
        }

        verify(exactly = 1) { linkService.get(EntryId("eid")) }
    }

}
