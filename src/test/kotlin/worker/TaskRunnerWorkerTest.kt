package worker

import common.TestCoroutineContext
import io.mockk.*
import kotlinx.coroutines.experimental.runBlocking
import org.junit.jupiter.api.Test
import task.Task
import task.TaskContext

class TaskRunnerWorkerTest {

    @Test
    fun testTaskWork(): Unit = runBlocking(TestCoroutineContext()) {

        val context = TaskContext(mapOf("a" to "1"))
        val task = mockk<Task<TaskContext>>()
        val request = TaskRunnerRequest(task, context)

        coEvery { task.process(context) } just Runs

        val taskRunnerWorker = TaskRunnerWorker().apply { runner = coroutineContext }.worker()
        taskRunnerWorker.send(request)
        taskRunnerWorker.close()

        coVerify(exactly = 1) { task.process(context) }

    }

}