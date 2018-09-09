package worker

import common.Link
import common.inject.ServiceProvider
import kotlinx.coroutines.experimental.channels.SendChannel
import resource.WebResourceRetriever
import task.Task
import task.TaskContext
import user.Preferences

class WorkerRegistry {

    fun init(serviceProvider: ServiceProvider) {
        with(serviceProvider) {
            linkWorker = LinkProcessorWorker(get(), get(), get()).worker()
            discussionWorker = DiscussionFinderWorker(get(),
                    WebResourceRetriever(), get()).worker()
            taskWorker = TaskRunnerWorker(get()).worker()
            unreadDigestWorker = UnreadLinkDigestWorker(get(), get(), get()).worker()
        }
    }

    private lateinit var linkWorker: SendChannel<LinkProcessingRequest>
    private lateinit var discussionWorker: SendChannel<Link>
    private lateinit var taskWorker: SendChannel<TaskRunnerRequest>
    private lateinit var unreadDigestWorker: SendChannel<UnreadLinkDigestWorkerRequest>

    fun acceptLinkWork(request: LinkProcessingRequest) {
        linkWorker.offer(request)
    }

    fun acceptTaskWork(task: Task<TaskContext>, context: TaskContext) {
        taskWorker.offer(TaskRunnerRequest(task, context))
    }

    fun acceptDiscussionWork(link: Link) {
        discussionWorker.offer(link)
    }

    fun onUserPreferenceChange(preferences: Preferences) {
        unreadDigestWorker.offer(UnreadLinkDigestWorkerRequest(preferences, CrudType.UPDATE))
    }

    private fun startScheduledWorkers(serviceProvider: ServiceProvider) {
        TempFileCleanupWorker(serviceProvider.get()).run()
    }

}