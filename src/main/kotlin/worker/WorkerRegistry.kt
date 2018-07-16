package worker

import common.Link
import common.inject.ServiceProvider
import kotlinx.coroutines.experimental.channels.SendChannel
import resource.WebResourceRetriever
import task.Task
import task.TaskContext

class WorkerRegistry {

    fun init(serviceProvider: ServiceProvider) {
        linkWorker = LinkProcessorWorker(serviceProvider.get(), serviceProvider.get(), serviceProvider.get()).worker()
        discussionWorker = DiscussionFinderWorker(serviceProvider.get(), serviceProvider.get(),
                WebResourceRetriever(), serviceProvider.get()).worker()
        taskWorker = TaskRunnerWorker(serviceProvider.get()).worker()
    }

    private lateinit var linkWorker: SendChannel<LinkProcessingRequest>
    private lateinit var discussionWorker: SendChannel<Link>
    private lateinit var taskWorker: SendChannel<TaskRunnerRequest>

    fun acceptLinkWork(request: LinkProcessingRequest) {
        linkWorker.offer(request)
    }

    fun acceptTaskWork(task: Task<TaskContext>, context: TaskContext) {
        taskWorker.offer(TaskRunnerRequest(task, context))
    }

    fun acceptDiscussionWork(link: Link) {
        discussionWorker.offer(link)
    }

    private fun startScheduledWorkers() {
        TempFileCleanupWorker().run()
    }
}