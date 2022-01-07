package lynks.task

import lynks.common.TaskParameter
import lynks.common.TaskParameterType
import lynks.common.inject.Inject
import lynks.resource.ResourceManager
import lynks.resource.ResourceRetriever
import lynks.util.Result
import lynks.util.loggerFor

class ResourceRetrievingTask(id: String, entryId: String) :
    Task<ResourceRetrievingTask.ResourceTaskContext>(id, entryId) {

    private val log = loggerFor<ResourceRetrievingTask>()

    @Inject
    lateinit var resourceManager: ResourceManager

    @Inject
    lateinit var resourceRetriever: ResourceRetriever

    override suspend fun process(context: ResourceTaskContext) {
        when (val result = resourceRetriever.getFileResult(context.url)) {
            is Result.Success -> {
                resourceManager.saveUploadedResource(entryId, context.name, result.value.inputStream())
            }
            is Result.Failure -> log.error("Error whilst retrieving resource", result.reason)
        }
    }

    override fun createContext(params: Map<String, String>): ResourceTaskContext {
        return ResourceTaskContext(params)
    }

    companion object {
        fun build(url: String, name: String): TaskBuilder {
            return TaskBuilder(
                ResourceRetrievingTask::class,
                listOf(
                    TaskParameter("url", TaskParameterType.STATIC, value = url),
                    TaskParameter("name", TaskParameterType.STATIC, value = name)
                )
            )
        }
    }

    class ResourceTaskContext(input: Map<String, String>) : TaskContext(input) {
        val url: String get() = param("url")
        val name: String get() = param("name")
    }
}

