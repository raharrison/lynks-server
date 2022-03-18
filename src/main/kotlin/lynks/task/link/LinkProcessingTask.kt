package lynks.task.link

import lynks.common.TaskParameter
import lynks.common.TaskParameterType
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
            val resourceSet = context.type ?: EnumSet.noneOf(ResourceType::class.java)
            workerRegistry.acceptLinkWork(PersistLinkProcessingRequest(it, resourceSet, true))
        }
    }

    override fun createContext(params: Map<String, String>) = LinkProcessingTaskContext(params)

    companion object {
        fun build(): TaskBuilder {
            return TaskBuilder(LinkProcessingTask::class)
        }

        fun buildAllTypes(): TaskBuilder {
            val options = ResourceType.linkBaseline().map { it.name }.toList()
            return TaskBuilder(LinkProcessingTask::class,
                listOf(TaskParameter("type", TaskParameterType.MULTI, "Resource Type", options = options))
            )
        }

        fun build(type: ResourceType): TaskBuilder {
            return TaskBuilder(LinkProcessingTask::class,
                listOf(TaskParameter("type", TaskParameterType.STATIC, "Resource Type", value = type.name))
            )
        }
    }

    class LinkProcessingTaskContext(input: Map<String, String>) : TaskContext(input) {
        val type: EnumSet<ResourceType>?
            get() = listParam("type")?.let { type ->
                EnumSet.copyOf(type.map { ResourceType.valueOf(it) })
            }
    }

}
