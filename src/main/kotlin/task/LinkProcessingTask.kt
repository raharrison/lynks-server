package task

import common.inject.Inject
import entry.LinkService
import resource.ResourceType
import worker.PersistLinkProcessingRequest
import worker.WorkerRegistry
import java.util.*

class LinkProcessingTask(id: String, entryId: String) :
    Task<LinkProcessingTask.LinkProcessingTaskContext>(id, entryId) {

    @Inject
    lateinit var workerRegistry: WorkerRegistry

    @Inject
    lateinit var linkService: LinkService

    override suspend fun process(context: LinkProcessingTaskContext) {
        linkService.get(entryId)?.let { it ->
            val resourceSet = context.type?.let { EnumSet.of(it) } ?: ResourceType.all()
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