package task.link

import common.inject.Inject
import entry.LinkService
import task.Task
import task.TaskBuilder
import task.TaskContext
import worker.WorkerRegistry

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

    override fun createContext(input: Map<String, String>) = TaskContext(input)

    companion object {
        fun build(): TaskBuilder {
            return TaskBuilder(DiscussionFinderTask::class, TaskContext())
        }
    }

}
