package link

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import common.BaseProperties
import kotlinx.coroutines.runBlocking
import resource.*
import task.youtube.YoutubeDlTask
import task.youtube.YoutubeSubtitleTask
import util.JsonMapper
import util.Result
import util.URLUtils
import util.loggerFor
import java.util.*

private val log = loggerFor<YoutubeLinkProcessor>()

class YoutubeLinkProcessor(
    url: String,
    webResourceRetriever: WebResourceRetriever,
    resourceManager: ResourceManager
) :
    LinkProcessor(url, webResourceRetriever, resourceManager) {

    private val apiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private val playerRequest = """
        {
                "context": {
                    "client": {
                        "clientName": "ANDROID",
                        "clientVersion": "16.20"
                    }
                },
                "api_key": "%s",
                "videoId": "%s"
        }
    """.trimIndent()

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
        if (responseJson.has("videoDetails")) {
            return responseJson["videoDetails"]
        }
        return null
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
        val url = "https://youtubei.googleapis.com/youtubei/v1/player?key=$apiKey"
        val requestBody = playerRequest.format(apiKey, videoId)
        return when(val response = webResourceRetriever.postStringResult(url, requestBody)) {
            is Result.Success -> response.value
            is Result.Failure -> null
        }
    }

    private suspend fun generateThumbnail(): GeneratedResource? {
        log.info("Capturing thumbnail for Youtube video videoId={}", videoId)
        val dl = "http://i3.ytimg.com/vi/$videoId/mqdefault.jpg"
        // "https://img.youtube.com/vi/$videoId/mqdefault.jpg"
        return webResourceRetriever.getFile(dl)?.let {
            val savedFile = resourceManager.saveTempFile(url, it, ResourceType.THUMBNAIL, JPG)
            GeneratedResource(ResourceType.THUMBNAIL, savedFile, JPG)
        }
    }

    private suspend fun generatePreview(): GeneratedResource? {
        log.info("Capturing preview for Youtube video videoId={}", videoId)
        val dl = "http://i3.ytimg.com/vi/$videoId/maxresdefault.jpg"
        // "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
        return webResourceRetriever.getFile(dl)?.let {
            val savedFile = resourceManager.saveTempFile(url, it, ResourceType.PREVIEW, JPG)
            GeneratedResource(ResourceType.PREVIEW, savedFile, JPG)
        }
    }

    override suspend fun enrich(props: BaseProperties) {
        super.enrich(props)
        props.addAttribute("embedUrl", embedUrl())
        addYoutubeDlTasks(url, props)
    }

    private fun addYoutubeDlTasks(url: String, props: BaseProperties) {
        props.addTask("Download Audio", YoutubeDlTask.build(url, YoutubeDlTask.YoutubeDlDownload.BEST_AUDIO))
        props.addTask("Download Video (max 720p)", YoutubeDlTask.build(url, YoutubeDlTask.YoutubeDlDownload.BEST_VIDEO))
        props.addTask(
            "Download Video (max 1080p)",
            YoutubeDlTask.build(url, YoutubeDlTask.YoutubeDlDownload.BEST_VIDEO_TRANSCODE)
        )
        props.addTask("Download Subtitles", YoutubeSubtitleTask.build())
    }
}
