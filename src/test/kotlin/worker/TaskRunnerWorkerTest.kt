package worker

import common.TestCoroutineContext
import io.mockk.*
import kotlinx.coroutines.runBlocking
import notify.NotifyService
import org.junit.jupiter.api.Test
import task.Task
import task.TaskContext

class TaskRunnerWorkerTest {

    @Test
    fun testTaskWork(): Unit = runBlocking(TestCoroutineContext()) {

        val context = TaskContext(mapOf("a" to "1"))
        val task = mockk<Task<TaskContext>>()
        coEvery { task.process(context) } just Runs

        val notifyService = mockk<NotifyService>(relaxUnitFun = true)
        coEvery { notifyService.accept(any(), null) } just Runs

        val taskRunnerWorker = TaskRunnerWorker(notifyService).apply { runner = this@runBlocking.coroutineContext }.worker()
        val request = TaskRunnerRequest(task, context)
        taskRunnerWorker.send(request)
        taskRunnerWorker.close()

        coVerify(exactly = 1) { task.process(context) }
        coVerify(exactly = 1) { notifyService.accept(any(), null) }

    }

}