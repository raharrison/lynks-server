package lynks.worker

import lynks.entry.EntryAuditService
import lynks.notify.Notification
import lynks.notify.NotifyService
import lynks.task.Task
import lynks.task.TaskContext

class TaskRunnerRequest(val task: Task<TaskContext>, val context: TaskContext)

class TaskRunnerWorker(notifyService: NotifyService, entryAuditService: EntryAuditService) :
    ChannelBasedWorker<TaskRunnerRequest>(notifyService, entryAuditService) {

    override suspend fun doWork(input: TaskRunnerRequest) {
        input.task.process(input.context)
        sendNotification(Notification.processed("Task submitted for processing"))
    }

}
