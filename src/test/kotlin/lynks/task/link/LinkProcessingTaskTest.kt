package lynks.task.link

import io.mockk.*
import kotlinx.coroutines.runBlocking
import lynks.common.Link
import lynks.entry.LinkService
import lynks.resource.ResourceType
import lynks.worker.PersistLinkProcessingRequest
import lynks.worker.WorkerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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
        assertThat(context).isInstanceOf(LinkProcessingTask.LinkProcessingTaskContext::class.java)
        assertThat(context.type).isNull()
    }

    @Test
    fun testContextConstructWithType() {
        val context = linkProcessingTask.createContext(mapOf("type" to "${ResourceType.SCREENSHOT.name},${ResourceType.DOCUMENT.name}"))
        assertThat(context).isInstanceOf(LinkProcessingTask.LinkProcessingTaskContext::class.java)
        assertThat(context.type).containsOnly(ResourceType.SCREENSHOT, ResourceType.DOCUMENT)
    }

    @Test
    fun testBuilder() {
        val builder = LinkProcessingTask.build()
        assertThat(builder.clazz).isEqualTo(LinkProcessingTask::class)
        assertThat(builder.params).isEmpty()

        val builderWithType = LinkProcessingTask.build(ResourceType.SCREENSHOT)
        assertThat(builder.clazz).isEqualTo(LinkProcessingTask::class)
        assertThat(builderWithType.params).hasSize(1).extracting("value").containsOnly(ResourceType.SCREENSHOT.name)

        val builderAllTypes = LinkProcessingTask.buildAllTypes()
        assertThat(builder.clazz).isEqualTo(LinkProcessingTask::class)
        assertThat(builderAllTypes.params).hasSize(1).extracting("name").containsOnly("type")
        assertThat(builderAllTypes.params).hasSize(1).extracting("value").containsOnlyNulls()
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
