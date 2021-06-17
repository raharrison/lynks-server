package link

import common.BaseProperties
import resource.GeneratedResource
import resource.ResourceManager
import resource.ResourceType
import resource.WebResourceRetriever
import task.link.DiscussionFinderTask
import task.link.LinkProcessingTask
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
