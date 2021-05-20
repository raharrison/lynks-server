package link

import common.BaseProperties
import link.extract.ExtractionPolicy
import link.extract.LinkContent
import resource.ResourceRetriever
import resource.ResourceType
import task.DiscussionFinderTask
import task.LinkProcessingTask
import java.util.*

abstract class LinkProcessor(
    protected val extractionPolicy: ExtractionPolicy,
    protected val url: String,
    protected val resourceRetriever: ResourceRetriever
) :
    AutoCloseable {

    abstract suspend fun init()

    abstract fun matches(): Boolean

    abstract val linkContent: LinkContent

    abstract suspend fun process(resourceSet: EnumSet<ResourceType>): Map<ResourceType, GeneratedResource>

    open suspend fun enrich(props: BaseProperties) {
        props.addTask("Process Link", LinkProcessingTask.build())
        props.addTask("Find Discussions", DiscussionFinderTask.build())
    }

    abstract val resolvedUrl: String

}
