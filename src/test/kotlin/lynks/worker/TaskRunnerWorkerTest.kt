package lynks.worker

import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import lynks.task.Task
import lynks.task.TaskContext
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class TaskRunnerWorkerTest {

    @Test
    fun testTaskWork(): Unit = runTest {
        val context = TaskContext(mapOf("a" to "1"))
        val task = mockk<Task<TaskContext>>()
        coEvery { task.process(context) } just Runs

        val taskRunnerWorker = TaskRunnerWorker()
            .apply { runner = this@runTest.coroutineContext }.worker()
        val request = TaskRunnerRequest(task, context)
        taskRunnerWorker.send(request)
        advanceUntilIdle()
        taskRunnerWorker.close()

        coVerify(exactly = 1) { task.process(context) }
    }

}
