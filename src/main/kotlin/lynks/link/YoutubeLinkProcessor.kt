package lynks.link

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import kotlinx.coroutines.runBlocking
import lynks.common.BaseProperties
import lynks.resource.*
import lynks.task.youtube.YoutubeDlAudioTask
import lynks.task.youtube.YoutubeDlVideoTask
import lynks.task.youtube.YoutubeSubtitleTask
import lynks.util.JsonMapper
import lynks.util.Result
import lynks.util.URLUtils
import lynks.util.loggerFor
import java.util.*

class YoutubeLinkProcessor(
    url: String,
    webResourceRetriever: WebResourceRetriever,
    resourceManager: ResourceManager
) :
    LinkProcessor(url, webResourceRetriever, resourceManager) {

    private val log = loggerFor<YoutubeLinkProcessor>()
    private lateinit var videoId: String

    private val videoInfo = lazy {
        runBlocking {
            downloadVideoInfo()?.let {
                parseVideoInfo(it)
            }
        }
    }

    override suspend fun init() {
        this.videoId = extractVideoId()
    }

    private fun extractVideoId(): String {
        return URLUtils.extractQueryParams(url)["v"] ?: throw IllegalArgumentException("Invalid youtube url")
    }

    private fun parseVideoInfo(raw: String): JsonNode? {
        val responseJson = JsonMapper.defaultMapper.readTree(raw)
        return if(responseJson.has("title")) responseJson else null
    }

    private fun extractKeywords(): Set<String> {
        val keywords = videoInfo.value?.get("keywords")
        if (keywords is ArrayNode) {
            return keywords.map { it.textValue() }.toSet()
        }
        return emptySet()
    }

    override fun close() {
    }

    override fun matches(): Boolean = URLUtils.extractSource(url) == "youtube.com"

    private suspend fun generateResources(resourceSet: EnumSet<ResourceType>): Map<ResourceType, GeneratedResource> {
        val generatedResources = mutableMapOf<ResourceType, GeneratedResource>()

        if (resourceSet.contains(ResourceType.THUMBNAIL)) {
            generateThumbnail()?.let {
                generatedResources[ResourceType.THUMBNAIL] = it
            }
        }
        if (resourceSet.contains(ResourceType.PREVIEW)) {
            generatePreview()?.let {
                generatedResources[ResourceType.PREVIEW] = it
            }
        }

        return generatedResources
    }

    override suspend fun scrapeResources(resourceSet: EnumSet<ResourceType>): List<GeneratedResource> {
        return generateResources(resourceSet).values.toList()
    }

    override suspend fun suggest(resourceSet: EnumSet<ResourceType>): SuggestResponse {
        val resources = generateResources(resourceSet)
        val title = videoInfo.value?.get("title")?.asText() ?: ""
        val keywords = extractKeywords()
        val linkDetails = LinkDetails(url, title, keywords)
        return SuggestResponse(linkDetails, resources.values.toList())
    }

    private fun embedUrl(): String = "https://www.youtube.com/embed/${extractVideoId()}"

    private suspend fun downloadVideoInfo(): String? {
        log.info("Retrieving video info for Youtube video id={}", videoId)
        val url = "https://yt.artemislena.eu/api/v1/videos/$videoId?fields=title%2Cdescription%2Ckeywords%2Cauthor%2CauthorId"
        return when(val response = webResourceRetriever.getStringResult(url)) {
            is Result.Success -> response.value
            is Result.Failure -> null
        }
    }

    private suspend fun generateThumbnail(): GeneratedResource? {
        log.info("Capturing thumbnail for Youtube video videoId={}", videoId)
        val dl = "https://i3.ytimg.com/vi/$videoId/mqdefault.jpg"
        // "https://img.youtube.com/vi/$videoId/mqdefault.jpg"
        return webResourceRetriever.getFile(dl)?.let {
            val savedFile = resourceManager.saveTempFile(url, it, ResourceType.THUMBNAIL, JPG)
            GeneratedResource(ResourceType.THUMBNAIL, savedFile, JPG)
        }
    }

    private suspend fun generatePreview(): GeneratedResource? {
        log.info("Capturing preview for Youtube video videoId={}", videoId)
        val dl = "https://i3.ytimg.com/vi/$videoId/maxresdefault.jpg"
        // "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
        return webResourceRetriever.getFile(dl)?.let {
            val savedFile = resourceManager.saveTempFile(url, it, ResourceType.PREVIEW, JPG)
            GeneratedResource(ResourceType.PREVIEW, savedFile, JPG)
        }
    }

    override suspend fun enrich(props: BaseProperties) {
        super.enrich(props)
        props.addAttribute("embedUrl", embedUrl())
        addYoutubeDlTasks(props)
    }

    private fun addYoutubeDlTasks(props: BaseProperties) {
        props.addTask("Download Video", YoutubeDlVideoTask.build())
        props.addTask("Download Audio", YoutubeDlAudioTask.build())
        props.addTask("Download Subtitles", YoutubeSubtitleTask.build())
    }
}
