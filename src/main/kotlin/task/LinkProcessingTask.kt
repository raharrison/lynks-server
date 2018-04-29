package task

import entry.LinkService
import worker.PersistLinkProcessingRequest
import worker.WorkerRegistry

class LinkProcessingTask(id: String, entryId: String) : Task(id, entryId) {

    lateinit var workerRegistry: WorkerRegistry
    lateinit var linkService: LinkService

    override suspend fun process(context: TaskContext) {
        linkService.get(entryId)?.let {
            workerRegistry.acceptLinkWork(PersistLinkProcessingRequest(it))
        }
    }

}