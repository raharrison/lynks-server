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
import org.junit.jupiter.api.assertThrows
import task.LinkProcessingTask
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
        val entry = Link("entry1", "title", "google.com", "src", "", 1234, 1234L, emptyList(), emptyList(), props)
        every { entryService.get("entry1") } returns entry
        every { workerRegistry.acceptTaskWork(any(), any()) } just Runs

        val res = taskService.runTask("entry1", "task1")

        assertThat(res).isTrue()

        val context = LinkProcessingTask.LinkProcessingTaskContext(mapOf("k1" to "v1"))
        verify(exactly = 1) { entryService.get("entry1") }
        verify { workerRegistry.acceptTaskWork(match {
            if(it::class == LinkProcessingTask::class) {
                val processingTask = it as LinkProcessingTask
                assertThat(processingTask.id).isEqualTo("task1")
                assertThat(processingTask.entryId).isEqualTo("entry1")
                assertThat(processingTask.workerRegistry).isEqualTo(workerRegistry)
                assertThat(processingTask.linkService).isEqualTo(linkService)
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
        props.addTask(
            TaskDefinition(
                "task1",
                "description",
                LinkProcessingTask::class.qualifiedName!!,
                mapOf("k1" to "v1")
            )
        )
        val entry = Link("entry1", "title", "google.com", "src", "", 1234, 1234, emptyList(), emptyList(), props)
        every { entryService.get("entry1") } returns entry

        val res = taskService.runTask("entry1", "invalid")
        assertThat(res).isFalse()
        verify(exactly = 1) { entryService.get("entry1") }
    }

    @Test
    fun testRunInvalidTaskClassThrows() {
        val props = BaseProperties().apply {
            addTask(TaskDefinition("task1", "description", TaskService::class.qualifiedName!!, mapOf("k1" to "v1")))
        }
        val entry = Link("entry1", "title", "google.com", "src", "", 1234, 1234L, emptyList(), emptyList(), props)
        every { entryService.get("entry1") } returns entry

        assertThrows<IllegalArgumentException> {
            taskService.runTask("entry1", "task1")
        }
    }
}
