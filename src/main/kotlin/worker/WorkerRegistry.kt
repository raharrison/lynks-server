package worker

import common.inject.ServiceProvider
import kotlinx.coroutines.channels.SendChannel
import resource.WebResourceRetriever
import task.Task
import task.TaskContext
import user.Preferences

class WorkerRegistry {

    fun init(serviceProvider: ServiceProvider) {
        with(serviceProvider) {
            linkWorker = LinkProcessorWorker(get(), get(), get(), get(), get()).worker()
            discussionWorker = DiscussionFinderWorker(get(),
                    WebResourceRetriever(), get(), get()).worker()
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
        linkWorker.offer(request)
    }

    fun acceptTaskWork(task: Task<TaskContext>, context: TaskContext) {
        taskWorker.offer(TaskRunnerRequest(task, context))
    }

    fun acceptDiscussionWork(linkId: String) {
        discussionWorker.offer(DiscussionFinderWorkerRequest(linkId))
    }

    fun acceptReminderWork(request: ReminderWorkerRequest) {
        reminderWorker.offer(request)
    }

    fun onUserPreferenceChange(preferences: Preferences) {
        unreadDigestWorker.offer(UnreadLinkDigestWorkerRequest(preferences))
        fileCleanupWorker.offer(TempFileCleanupWorkerRequest(preferences))
    }
}