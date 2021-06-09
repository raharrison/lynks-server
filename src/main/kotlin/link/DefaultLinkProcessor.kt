package link

import com.fasterxml.jackson.module.kotlin.readValue
import common.BaseProperties
import common.Environment
import resource.GeneratedResource
import resource.ResourceManager
import resource.ResourceType
import resource.WebResourceRetriever
import task.LinkProcessingTask
import task.LinkSummarizerTask
import util.JsonMapper
import util.Result
import java.util.*
import kotlin.io.path.absolutePathString

open class DefaultLinkProcessor(
    url: String,
    webResourceRetriever: WebResourceRetriever,
    resourceManager: ResourceManager
) :
    LinkProcessor(url, webResourceRetriever, resourceManager) {

    override suspend fun enrich(props: BaseProperties) {
        super.enrich(props)
        props.addTask("Generate Screenshot", LinkProcessingTask.build(ResourceType.SCREENSHOT))
        props.addTask("Generate Document", LinkProcessingTask.build(ResourceType.DOCUMENT))
        props.addTask("Generate Readable Page", LinkProcessingTask.build(ResourceType.READABLE_DOC))
        props.addTask("Generate Summary", LinkSummarizerTask.build())
    }

    override fun matches(): Boolean = true

    private data class ScrapeRequest(val url: String, val resourceTypes: List<ResourceType>, val targetPath: String)

    override suspend fun scrapeResources(resourceSet: EnumSet<ResourceType>): List<GeneratedResource> {
        val targetPath = resourceManager.constructTempBasePath(url)
        val scrapeUrl = Environment.external.scraperHost + "/scrape"
        val scrapeRequest = ScrapeRequest(url, resourceSet.toList(), targetPath.absolutePathString())

        return when (val result = webResourceRetriever.postStringResult(scrapeUrl, scrapeRequest)) {
            is Result.Failure -> throw result.reason
            is Result.Success -> JsonMapper.defaultMapper.readValue(result.value)
        }
    }

    override suspend fun suggest(resourceSet: EnumSet<ResourceType>): SuggestResponse {
        val targetPath = resourceManager.constructTempBasePath(url)
        val suggestUrl = Environment.external.scraperHost + "/suggest"
        val suggestRequest = ScrapeRequest(url, resourceSet.toList(), targetPath.absolutePathString())

        when (val result = webResourceRetriever.postStringResult(suggestUrl, suggestRequest)) {
            is Result.Failure -> throw result.reason
            is Result.Success -> return JsonMapper.defaultMapper.readValue(result.value)
        }
    }

}
