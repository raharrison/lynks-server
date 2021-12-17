package lynks.worker

import io.mockk.*
import kotlinx.coroutines.runBlocking
import lynks.common.TestCoroutineContext
import lynks.entry.EntryAuditService
import lynks.notify.NotifyService
import lynks.task.Task
import lynks.task.TaskContext
import org.junit.jupiter.api.Test

class TaskRunnerWorkerTest {

    @Test
    fun testTaskWork(): Unit = runBlocking(TestCoroutineContext()) {

        val context = TaskContext(mapOf("a" to "1"))
        val task = mockk<Task<TaskContext>>()
        coEvery { task.process(context) } just Runs

        val notifyService = mockk<NotifyService>(relaxUnitFun = true)
        coEvery { notifyService.accept(any(), null) } just Runs

        val entryAuditService = mockk<EntryAuditService>(relaxUnitFun = true)

        val taskRunnerWorker = TaskRunnerWorker(notifyService, entryAuditService)
            .apply { runner = this@runBlocking.coroutineContext }.worker()
        val request = TaskRunnerRequest(task, context)
        taskRunnerWorker.send(request)
        taskRunnerWorker.close()

        coVerify(exactly = 1) { task.process(context) }
        coVerify(exactly = 1) { notifyService.accept(any(), null) }

    }

}
