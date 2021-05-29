package worker

import common.inject.ServiceProvider
import kotlinx.coroutines.channels.SendChannel
import task.Task
import task.TaskContext
import user.Preferences

class WorkerRegistry {

    fun init(serviceProvider: ServiceProvider) {
        with(serviceProvider) {
            linkWorker = LinkProcessorWorker(get(), get(), get(), get(), get()).worker()
            discussionWorker = DiscussionFinderWorker(get(),
                    get(), get(), get()).worker()
            taskWorker = TaskRunnerWorker(get(), get()).worker()
            unreadDigestWorker = UnreadLinkDigestWorker(get(), get(), get(), get()).worker()
            fileCleanupWorker = TempFileCleanupWorker(get(), get(), get()).worker()
            reminderWorker = ReminderWorker(get(), get(), get(), get()).worker()
        }
    }

    private lateinit var linkWorker: SendChannel<LinkProcessingRequest>
    private lateinit var discussionWorker: SendChannel<DiscussionFinderWorkerRequest>
    private lateinit var taskWorker: SendChannel<TaskRunnerRequest>
    private lateinit var unreadDigestWorker: SendChannel<UnreadLinkDigestWorkerRequest>
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

    fun onUserPreferenceChange(preferences: Preferences) {
        unreadDigestWorker.trySend(UnreadLinkDigestWorkerRequest(preferences))
        fileCleanupWorker.trySend(TempFileCleanupWorkerRequest(preferences))
    }
}
