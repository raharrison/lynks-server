package lynks.service

import io.mockk.*
import lynks.common.*
import lynks.common.exception.InvalidModelException
import lynks.common.inject.ServiceProvider
import lynks.entry.EntryService
import lynks.entry.LinkService
import lynks.task.TaskService
import lynks.task.link.LinkProcessingTask
import lynks.worker.WorkerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

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
        props.addTask(
            TaskDefinition(
                TaskId("task1"), "description", LinkProcessingTask::class.qualifiedName!!,
                listOf(
                    TaskParameter("p1", TaskParameterType.STATIC, value = "v1"),
                    TaskParameter("p2", TaskParameterType.TEXT),
                    TaskParameter("p3", TaskParameterType.ENUM, options = listOf("e1", "e2")),
                    TaskParameter("p4", TaskParameterType.NUMBER, required = false)
                )
            )
        )
        val entry = Link(EntryId("entry1"), "title", "google.com", "src", "", Instant.EPOCH, Instant.EPOCH, emptyList(), emptyList(), props)
        every { entryService.get(EntryId("entry1")) } returns entry
        every { workerRegistry.acceptTaskWork(any(), any()) } just Runs

        val res = taskService.runTask(EntryId("entry1"), TaskId("task1"), mapOf("p1" to "v3", "p2" to "v2", "p3" to "e2"))

        assertThat(res).isTrue()

        val context = LinkProcessingTask.LinkProcessingTaskContext(mapOf("p1" to "v1", "p2" to "v2", "p3" to "e2"))
        verify(exactly = 1) { entryService.get(EntryId("entry1")) }
        verify { workerRegistry.acceptTaskWork(match {
            if(it::class == LinkProcessingTask::class) {
                val processingTask = it as LinkProcessingTask
                assertThat(processingTask.id).isEqualTo(TaskId("task1"))
                assertThat(processingTask.entryId).isEqualTo(EntryId("entry1"))
                assertThat(processingTask.workerRegistry).isEqualTo(workerRegistry)
                assertThat(processingTask.linkService).isEqualTo(linkService)
                return@match true
            }
            false
        }, context) }
    }

    @Test
    fun testRunTaskRequiredParamMissing() {
        val props = BaseProperties()
        props.addTask(
            TaskDefinition(
                TaskId("task1"), "description", LinkProcessingTask::class.qualifiedName!!,
                listOf(
                    TaskParameter("p1", TaskParameterType.STATIC, value = "v1"),
                    TaskParameter("p2", TaskParameterType.TEXT)
                )
            )
        )
        val entry = Link(EntryId("entry1"), "title", "google.com", "src", "", Instant.EPOCH, Instant.EPOCH, emptyList(), emptyList(), props)
        every { entryService.get(EntryId("entry1")) } returns entry

        assertThrows<InvalidModelException> { taskService.runTask(EntryId("entry1"), TaskId("task1"), emptyMap()) }
    }

    @Test
    fun testRunTaskInvalidEnumParam() {
        val props = BaseProperties()
        props.addTask(
            TaskDefinition(
                TaskId("task1"), "description", LinkProcessingTask::class.qualifiedName!!,
                listOf(
                    TaskParameter("p1", TaskParameterType.ENUM, options = listOf("v1", "v2"))
                )
            )
        )
        val entry = Link(EntryId("entry1"), "title", "google.com", "src", "", Instant.EPOCH, Instant.EPOCH, emptyList(), emptyList(), props)
        every { entryService.get(EntryId("entry1")) } returns entry

        assertThrows<InvalidModelException> { taskService.runTask(EntryId("entry1"), TaskId("task1"), mapOf("p1" to "invalid")) }
    }

    @Test
    fun testNoEntryReturnsFalse() {
        every { entryService.get(EntryId("invalid")) } returns null
        val res = taskService.runTask(EntryId("invalid"), TaskId("task1"), emptyMap())
        assertThat(res).isFalse()
        verify(exactly = 1) { entryService.get(EntryId("invalid")) }
    }

    @Test
    fun testNoTaskReturnsFalse() {
        val props = BaseProperties()
        props.addTask(
            TaskDefinition(
                TaskId("task1"),
                "description",
                LinkProcessingTask::class.qualifiedName!!
            )
        )
        val entry = Link(EntryId("entry1"), "title", "google.com", "src", "", Instant.EPOCH, Instant.EPOCH, emptyList(), emptyList(), props)
        every { entryService.get(EntryId("entry1")) } returns entry

        val res = taskService.runTask(EntryId("entry1"), TaskId("invalid"), emptyMap())
        assertThat(res).isFalse()
        verify(exactly = 1) { entryService.get(EntryId("entry1")) }
    }

    @Test
    fun testRunInvalidTaskClassThrows() {
        val props = BaseProperties().apply {
            addTask(TaskDefinition(TaskId("task1"), "description", TaskService::class.qualifiedName!!))
        }
        val entry = Link(EntryId("entry1"), "title", "google.com", "src", "", Instant.EPOCH, Instant.EPOCH, emptyList(), emptyList(), props)
        every { entryService.get(EntryId("entry1")) } returns entry

        assertThrows<IllegalArgumentException> {
            taskService.runTask(EntryId("entry1"), TaskId("task1"), emptyMap())
        }
    }
}
