package worker

import common.Link
import entry.LinkService
import kotlinx.coroutines.experimental.channels.SendChannel
import resource.ResourceManager
import resource.WebResourceRetriever
import schedule.ScheduleService
import task.Task
import task.TaskContext

class WorkerRegistry {

    fun init(resourceManager: ResourceManager, linkService: LinkService, scheduleService: ScheduleService) {
        linkWorker = LinkProcessorWorker(resourceManager, linkService).worker()
        discussionWorker = DiscussionFinderWorker(linkService, scheduleService, WebResourceRetriever()).worker()
    }

    private lateinit var linkWorker: SendChannel<LinkProcessingRequest>
    private lateinit var discussionWorker: SendChannel<Link>
    private val taskWorker = TaskRunnerWorker().worker()

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