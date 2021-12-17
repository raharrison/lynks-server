package lynks.task.link

import lynks.common.inject.Inject
import lynks.entry.LinkService
import lynks.resource.ResourceType
import lynks.task.Task
import lynks.task.TaskBuilder
import lynks.task.TaskContext
import lynks.worker.PersistLinkProcessingRequest
import lynks.worker.WorkerRegistry
import java.util.*

class LinkProcessingTask(id: String, entryId: String) :
    Task<LinkProcessingTask.LinkProcessingTaskContext>(id, entryId) {

    @Inject
    lateinit var workerRegistry: WorkerRegistry

    @Inject
    lateinit var linkService: LinkService

    override suspend fun process(context: LinkProcessingTaskContext) {
        linkService.get(entryId)?.also { it ->
            val resourceSet = context.type?.let { EnumSet.of(it) } ?: ResourceType.linkBaseline()
            workerRegistry.acceptLinkWork(PersistLinkProcessingRequest(it, resourceSet, true))
        }
    }

    override fun createContext(input: Map<String, String>) = LinkProcessingTaskContext(input)

    companion object {
        fun build(): TaskBuilder {
            return TaskBuilder(LinkProcessingTask::class, LinkProcessingTaskContext())
        }

        fun build(type: ResourceType): TaskBuilder {
            return TaskBuilder(LinkProcessingTask::class, LinkProcessingTaskContext(type))
        }
    }

    class LinkProcessingTaskContext(input: Map<String, String>) : TaskContext(input) {

        constructor() : this(emptyMap())

        constructor(type: ResourceType) : this(mapOf("type" to type.name))

        val type: ResourceType?
            get() = optParam("type")?.let {
                ResourceType.valueOf(it)
            }

    }

}
