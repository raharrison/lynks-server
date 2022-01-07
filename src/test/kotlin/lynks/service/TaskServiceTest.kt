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
                "task1", "description", LinkProcessingTask::class.qualifiedName!!,
                listOf(
                    TaskParameter("p1", TaskParameterType.STATIC, value = "v1"),
                    TaskParameter("p2", TaskParameterType.TEXT),
                    TaskParameter("p3", TaskParameterType.ENUM, options = setOf("e1", "e2"))
                )
            )
        )
        val entry = Link("entry1", "title", "google.com", "src", "", 1234, 1234L, emptyList(), emptyList(), props)
        every { entryService.get("entry1") } returns entry
        every { workerRegistry.acceptTaskWork(any(), any()) } just Runs

        val res = taskService.runTask("entry1", "task1", mapOf("p1" to "v3", "p2" to "v2", "p3" to "e2"))

        assertThat(res).isTrue()

        val context = LinkProcessingTask.LinkProcessingTaskContext(mapOf("p1" to "v1", "p2" to "v2", "p3" to "e2"))
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
    fun testRunTaskRequiredParamMissing() {
        val props = BaseProperties()
        props.addTask(
            TaskDefinition(
                "task1", "description", LinkProcessingTask::class.qualifiedName!!,
                listOf(
                    TaskParameter("p1", TaskParameterType.STATIC, value = "v1"),
                    TaskParameter("p2", TaskParameterType.TEXT)
                )
            )
        )
        val entry = Link("entry1", "title", "google.com", "src", "", 1234, 1234L, emptyList(), emptyList(), props)
        every { entryService.get("entry1") } returns entry

        assertThrows<InvalidModelException> { taskService.runTask("entry1", "task1", emptyMap()) }
    }

    @Test
    fun testRunTaskInvalidEnumParam() {
        val props = BaseProperties()
        props.addTask(
            TaskDefinition(
                "task1", "description", LinkProcessingTask::class.qualifiedName!!,
                listOf(
                    TaskParameter("p1", TaskParameterType.ENUM, options = setOf("v1", "v2"))
                )
            )
        )
        val entry = Link("entry1", "title", "google.com", "src", "", 1234, 1234L, emptyList(), emptyList(), props)
        every { entryService.get("entry1") } returns entry

        assertThrows<InvalidModelException> { taskService.runTask("entry1", "task1", mapOf("p1" to "invalid")) }
    }

    @Test
    fun testNoEntryReturnsFalse() {
        every { entryService.get("invalid") } returns null
        val res = taskService.runTask("invalid", "task1", emptyMap())
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
                LinkProcessingTask::class.qualifiedName!!
            )
        )
        val entry = Link("entry1", "title", "google.com", "src", "", 1234, 1234, emptyList(), emptyList(), props)
        every { entryService.get("entry1") } returns entry

        val res = taskService.runTask("entry1", "invalid", emptyMap())
        assertThat(res).isFalse()
        verify(exactly = 1) { entryService.get("entry1") }
    }

    @Test
    fun testRunInvalidTaskClassThrows() {
        val props = BaseProperties().apply {
            addTask(TaskDefinition("task1", "description", TaskService::class.qualifiedName!!))
        }
        val entry = Link("entry1", "title", "google.com", "src", "", 1234, 1234L, emptyList(), emptyList(), props)
        every { entryService.get("entry1") } returns entry

        assertThrows<IllegalArgumentException> {
            taskService.runTask("entry1", "task1", emptyMap())
        }
    }
}
