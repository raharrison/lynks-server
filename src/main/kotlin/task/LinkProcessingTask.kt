package task

import entry.LinkService
import worker.PersistLinkProcessingRequest
import worker.WorkerRegistry

class LinkProcessingTask(id: String, entryId: String) : Task<TaskContext>(id, entryId) {

    lateinit var workerRegistry: WorkerRegistry
    lateinit var linkService: LinkService

    override suspend fun process(context: TaskContext) {
        linkService.get(entryId)?.let {
            workerRegistry.acceptLinkWork(PersistLinkProcessingRequest(it))
        }
    }

    override fun createContext(input: Map<String, String>) = TaskContext(input)

    companion object {
        fun build(): TaskBuilder {
            return TaskBuilder(LinkProcessingTask::class, TaskContext())
        }
    }

}