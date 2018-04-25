package task

import entry.LinkService
import worker.PersistLinkProcessingRequest
import worker.WorkerRegistry

class LinkProcessingTask : Task {

    lateinit var workerRegistry: WorkerRegistry
    lateinit var linkService: LinkService

    override suspend fun process(context: TaskContext) {
        linkService.get(context.entryId)?.let {
            workerRegistry.acceptLinkWork(PersistLinkProcessingRequest(it))
        }
    }

}