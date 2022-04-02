package lynks.worker

import kotlinx.coroutines.channels.SendChannel
import lynks.common.inject.ServiceProvider
import lynks.task.Task
import lynks.task.TaskContext

class WorkerRegistry {

    fun init(serviceProvider: ServiceProvider) {
        with(serviceProvider) {
            linkWorker = LinkProcessorWorker(get(), get(), get(), get(), get()).worker()
            discussionWorker = DiscussionFinderWorker(get(), get(), get(), get()).worker()
            taskWorker = TaskRunnerWorker().worker()
            unreadDigestWorker = UnreadLinkDigestWorker(get(), get(), get()).worker()
            fileCleanupWorker = TempFileCleanupWorker().worker()
            reminderWorker = ReminderWorker(get(), get(), get()).worker()
        }
    }

    private lateinit var linkWorker: SendChannel<LinkProcessingRequest>
    private lateinit var discussionWorker: SendChannel<DiscussionFinderWorkerRequest>
    private lateinit var taskWorker: SendChannel<TaskRunnerRequest>
    private lateinit var unreadDigestWorker: SendChannel<String>
    private lateinit var fileCleanupWorker: SendChannel<TempFileCleanupWorkerRequest>
    private lateinit var reminderWorker: SendChannel<ReminderWorkerRequest>

    fun acceptLinkWork(request: LinkProcessingRequest) {
        linkWorker.trySend(request)
    }

    fun acceptTaskWork(task: Task<TaskContext>, context: TaskContext) {
        taskWorker.trySend(TaskRunnerRequest(task, context))
    }

    fun acceptDiscussionWork(linkId: String) {
        discussionWorker.trySend(DiscussionFinderWorkerRequest(linkId))
    }

    fun acceptReminderWork(request: ReminderWorkerRequest) {
        reminderWorker.trySend(request)
    }

}
