package worker

import entry.LinkService
import kotlinx.coroutines.experimental.channels.SendChannel
import resource.ResourceManager
import task.Task
import task.TaskContext

class WorkerRegistry {

    fun init(resourceManager: ResourceManager, linkService: LinkService) {
        linkWorker = LinkProcessorWorker(resourceManager, linkService).worker()
    }

    private lateinit var linkWorker: SendChannel<LinkProcessingRequest>
    private val taskWorker = TaskRunnerWorker().worker()

    fun acceptLinkWork(request: LinkProcessingRequest) {
        linkWorker.offer(request)
    }

    fun acceptTaskWork(task: Task<TaskContext>, context: TaskContext) {
        taskWorker.offer(TaskRunnerRequest(task, context))
    }

    private fun startScheduledWorkers() {
        TempFileCleanupWorker().run()
    }
}