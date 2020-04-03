package task

import common.Link
import entry.LinkService
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import resource.ResourceType
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
    fun testContextConstructNoType() {
        val context = linkProcessingTask.createContext(emptyMap())
        assertThat(context.input).isEmpty()
        assertThat(context).isInstanceOf(LinkProcessingTask.LinkProcessingTaskContext::class.java)
        assertThat(context.type).isNull()
    }

    @Test
    fun testContextConstructWithType() {
        val context = linkProcessingTask.createContext(mapOf("type" to ResourceType.SCREENSHOT.name))
        assertThat(context.input).hasSize(1).containsExactly(entry("type", ResourceType.SCREENSHOT.name))
        assertThat(context).isInstanceOf(LinkProcessingTask.LinkProcessingTaskContext::class.java)
        assertThat(context.type).isEqualTo(ResourceType.SCREENSHOT)
    }

    @Test
    fun testBuilder() {
        val builder = LinkProcessingTask.build()
        assertThat(builder.clazz).isEqualTo(LinkProcessingTask::class)
        assertThat(builder.context.input).isEmpty()

        val builderWithType = LinkProcessingTask.build(ResourceType.SCREENSHOT)
        assertThat(builder.clazz).isEqualTo(LinkProcessingTask::class)
        assertThat(builderWithType.context.input).hasSize(1).containsExactly(entry("type", ResourceType.SCREENSHOT.name))
    }

    @Test
    fun testProcessNoType() {
        val context = linkProcessingTask.createContext(emptyMap())
        val link = Link("eid", "title", "url", "", "", 1, 1)

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
    fun testProcessWithType() {
        val context = linkProcessingTask.createContext(mapOf("type" to ResourceType.SCREENSHOT.name))
        val link = Link("eid", "title", "url", "", "", 1, 1)

        every { linkService.get("eid") } returns link
        every { workerRegistry.acceptLinkWork(any()) } just Runs

        runBlocking {
            linkProcessingTask.process(context)
        }

        verify(exactly = 1) { linkService.get("eid") }
        verify(exactly = 1) { workerRegistry.acceptLinkWork(match {
            it is PersistLinkProcessingRequest && it.link == link && it.resourceSet.contains(ResourceType.SCREENSHOT)
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