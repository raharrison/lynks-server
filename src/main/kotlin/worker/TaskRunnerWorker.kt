package worker

import entry.EntryAuditService
import notify.NotifyService
import task.Task
import task.TaskContext

class TaskRunnerRequest(val task: Task<TaskContext>, val context: TaskContext)

class TaskRunnerWorker(notifyService: NotifyService, entryAuditService: EntryAuditService) :
    ChannelBasedWorker<TaskRunnerRequest>(notifyService, entryAuditService) {

    override suspend fun doWork(input: TaskRunnerRequest) {
        input.task.process(input.context)
        sendNotification()
    }

}