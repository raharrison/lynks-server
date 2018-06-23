package service

import common.BaseProperties
import common.Link
import common.TaskDefinition
import common.inject.ServiceProvider
import entry.EntryService
import entry.LinkService
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import task.LinkProcessingTask
import task.TaskContext
import task.TaskService
import worker.WorkerRegistry

class TaskServiceTest {
    private val workerRegistry = mockk<WorkerRegistry>()
    private val linkService = mockk<LinkService>()
    private val entryService = mockk<EntryService>()
    private val serviceProvider = ServiceProvider()
    private val taskService = TaskService(entryService, serviceProvider, workerRegistry)

    @BeforeEach
    fun before() {
        serviceProvider.apply {
            register(linkService)
            register(workerRegistry)
        }
    }

    @Test
    fun testRunValidTask() {

        val props = BaseProperties()
        props.addTask(TaskDefinition("task1", "description", LinkProcessingTask::class.qualifiedName!!, mapOf("k1" to "v1")))
        val entry = Link("entry1", "title", "google.com", "src", "", 1234, emptyList(), props)
        every { entryService.get("entry1") } returns entry
        every { workerRegistry.acceptTaskWork(any(), any()) } just Runs

        val res = taskService.runTask("entry1", "task1")

        assertThat(res).isTrue()

        val context = TaskContext(mapOf("k1" to "v1"))
        verify(exactly = 1) { entryService.get("entry1") }
        verify { workerRegistry.acceptTaskWork(match {
            if(it is LinkProcessingTask) {
                assertThat(it.id).isEqualTo("task1")
                assertThat(it.entryId).isEqualTo("entry1")
                assertThat(it.workerRegistry).isEqualTo(workerRegistry)
                assertThat(it.linkService).isEqualTo(linkService)
                return@match true
            }
            false
        }, context) }

    }

    @Test
    fun testNoEntryReturnsFalse() {
        every { entryService.get("invalid") } returns null
        val res = taskService.runTask("invalid", "task1")
        assertThat(res).isFalse()
        verify(exactly = 1) { entryService.get("invalid") }
    }

    @Test
    fun testNoTaskReturnsFalse() {
        val props = BaseProperties()
        props.addTask(TaskDefinition("task1", "description", LinkProcessingTask::class.qualifiedName!!, mapOf("k1" to "v1")))
        val entry = Link("entry1", "title", "google.com", "src", "", 1234, emptyList(), props)
        every { entryService.get("entry1") } returns entry

        val res = taskService.runTask("entry1", "invalid")
        assertThat(res).isFalse()
        verify(exactly = 1) { entryService.get("entry1") }
    }

}