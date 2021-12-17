package lynks.link

import lynks.common.BaseProperties
import lynks.resource.GeneratedResource
import lynks.resource.ResourceManager
import lynks.resource.ResourceType
import lynks.resource.WebResourceRetriever
import lynks.task.link.DiscussionFinderTask
import lynks.task.link.LinkProcessingTask
import java.util.*

abstract class LinkProcessor(
    val url: String,
    protected val webResourceRetriever: WebResourceRetriever,
    protected val resourceManager: ResourceManager
) :
    AutoCloseable {

    open suspend fun init() {}

    override fun close() {}

    abstract fun matches(): Boolean

    abstract suspend fun scrapeResources(resourceSet: EnumSet<ResourceType>): List<GeneratedResource>

    abstract suspend fun suggest(resourceSet: EnumSet<ResourceType>): SuggestResponse

    open suspend fun enrich(props: BaseProperties) {
        props.addTask("Process Link", LinkProcessingTask.build())
        props.addTask("Find Discussions", DiscussionFinderTask.build())
    }

}
