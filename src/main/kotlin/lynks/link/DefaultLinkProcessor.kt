package lynks.link

import com.fasterxml.jackson.module.kotlin.readValue
import lynks.common.BaseProperties
import lynks.common.Environment
import lynks.common.exception.SuggestionUnavailableException
import lynks.resource.GeneratedResource
import lynks.resource.ResourceManager
import lynks.resource.ResourceType
import lynks.resource.WebResourceRetriever
import lynks.task.link.LinkProcessingTask
import lynks.util.JsonMapper
import lynks.util.Result
import java.time.Duration
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
        props.addTask("Generate Single File", LinkProcessingTask.build(ResourceType.SINGLE_FILE))
    }

    override fun matches(): Boolean = true

    private data class ScrapeRequest(val url: String, val resourceTypes: List<ResourceType>, val targetPath: String)

    override suspend fun scrapeResources(resourceSet: EnumSet<ResourceType>): List<GeneratedResource> {
        val targetPath = resourceManager.constructTempBasePath(url)
        val scrapeUrl = Environment.external.scraperHost + "/scrape"
        val scrapeRequest = ScrapeRequest(url, resourceSet.toList(), targetPath.absolutePathString())

        return when (val result = webResourceRetriever.postStringResult(scrapeUrl, scrapeRequest, SCRAPE_TIMEOUT)) {
            is Result.Failure -> throw result.reason
            is Result.Success -> JsonMapper.defaultMapper.readValue(result.value)
        }
    }

    override suspend fun suggest(resourceSet: EnumSet<ResourceType>): SuggestResponse {
        val scraperHost = Environment.external.scraperHost
            ?: throw SuggestionUnavailableException("Scraper not configured")
        val targetPath = resourceManager.constructTempBasePath(url)
        val suggestUrl = "$scraperHost/suggest"
        val suggestRequest = ScrapeRequest(url, resourceSet.toList(), targetPath.absolutePathString())

        return when (val result = webResourceRetriever.postStringResult(suggestUrl, suggestRequest, SUGGEST_TIMEOUT)) {
            is Result.Failure -> throw SuggestionUnavailableException(
                "Scraper request failed: ${result.reason.message}",
                result.reason
            )

            is Result.Success -> JsonMapper.defaultMapper.readValue(result.value)
        }
    }

    private companion object {
        // headless Chrome waits up to 60s for the page, then renders every requested resource
        val SCRAPE_TIMEOUT: Duration = Duration.ofMinutes(5)

        // the scraper allows 30s each for the page and its main image
        val SUGGEST_TIMEOUT: Duration = Duration.ofSeconds(90)
    }

}
