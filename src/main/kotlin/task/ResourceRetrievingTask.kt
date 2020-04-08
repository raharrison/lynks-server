package task

import common.inject.Inject
import resource.ResourceManager
import resource.ResourceRetriever
import util.Result
import util.loggerFor

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

    override fun createContext(input: Map<String, String>): ResourceTaskContext {
        return ResourceTaskContext(input)
    }

    companion object {
        fun build(url: String, name: String): TaskBuilder {
            return TaskBuilder(ResourceRetrievingTask::class, ResourceTaskContext(url, name))
        }
    }

    class ResourceTaskContext(input: Map<String, String>) : TaskContext(input) {

        constructor(url: String, name: String) : this(mapOf("url" to url, "name" to name))

        val url: String get() = param("url")
        val name: String get() = param("name")

    }
}

