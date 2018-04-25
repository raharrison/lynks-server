package task

import entry.LinkService
import worker.PersistLinkProcessingRequest
import worker.WorkerRegistry

class LinkProcessingTask(var workerRegistry: WorkerRegistry, var linkService: LinkService) : Task {

    override suspend fun process(context: TaskContext) {
        linkService.get(context.entryId)?.let {
            workerRegistry.acceptLinkWork(PersistLinkProcessingRequest(it))
        }
    }

}