package lynks.task.link

import lynks.common.inject.Inject
import lynks.entry.LinkService
import lynks.task.Task
import lynks.task.TaskBuilder
import lynks.task.TaskContext
import lynks.worker.WorkerRegistry

class DiscussionFinderTask(id: String, entryId: String) : Task<TaskContext>(id, entryId) {

    @Inject
    lateinit var workerRegistry: WorkerRegistry

    @Inject
    lateinit var linkService: LinkService

    override suspend fun process(context: TaskContext) {
        linkService.get(entryId)?.also {
            workerRegistry.acceptDiscussionWork(it.id)
        }
    }

    override fun createContext(params: Map<String, String>) = TaskContext(params)

    companion object {
        fun build(): TaskBuilder {
            return TaskBuilder(DiscussionFinderTask::class)
        }
    }

}
