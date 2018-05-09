package task

import resource.ResourceManager
import resource.ResourceRetriever

class ResourceRetrievingTask(id: String, entryId: String) : Task<ResourceRetrievingTask.ResourceTaskContext>(id, entryId) {

    lateinit var resourceManager: ResourceManager
    lateinit var resourceRetriever: ResourceRetriever

    override suspend fun process(context: ResourceTaskContext) {
        val bytes = resourceRetriever.getFile(context.url)
        bytes?.let {
            resourceManager.saveUploadedResource(entryId, context.name, bytes.inputStream())
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

