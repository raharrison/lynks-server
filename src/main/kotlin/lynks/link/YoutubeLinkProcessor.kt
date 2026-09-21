package lynks.link

import lynks.common.BaseProperties
import lynks.common.Environment
import lynks.common.exception.SuggestionUnavailableException
import lynks.resource.*
import lynks.util.JsonMapper
import lynks.util.Result
import lynks.util.URLUtils
import lynks.util.loggerFor
import java.net.URI
import java.util.*

// extractSource strips a leading "www." so only the bare hosts are listed
private val YOUTUBE_HOSTS = setOf(
    "youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be"
)

data class VideoInfo(
    val title: String,
    val description: String?,
    val tags: Set<String>,
    val channelTitle: String?,
    val publishedAt: String?,
    val thumbnailUrl: String?,
    val previewUrl: String?
)

class YoutubeLinkProcessor(
    url: String,
    webResourceRetriever: WebResourceRetriever,
    resourceManager: ResourceManager,
    private val apiKey: String? = Environment.external.youtubeApiKey
) : LinkProcessor(url, webResourceRetriever, resourceManager) {

    private val log = loggerFor<YoutubeLinkProcessor>()
    private var videoId: String? = null
    private var videoInfo: VideoInfo? = null

    override suspend fun init() {
        videoId = extractVideoId()
        if (apiKey != null && videoId != null) {
            videoInfo = fetchVideoInfo(videoId!!, apiKey)
        }
    }

    override fun matches(): Boolean = URLUtils.extractSource(url) in YOUTUBE_HOSTS

    private fun extractVideoId(): String? {
        val params = URLUtils.extractQueryParams(url)
        if (params.containsKey("v")) return params["v"]

        val path = try {
            URI(url).path
        } catch (e: Exception) {
            return null
        } ?: return null
        val segments = path.split("/").filter { it.isNotBlank() }

        val prefixedSegments = setOf("shorts", "embed", "live", "e")
        val idx = segments.indexOfFirst { it in prefixedSegments }
        if (idx != -1 && idx + 1 < segments.size) return segments[idx + 1]

        if (URLUtils.extractSource(url) == "youtu.be" && segments.isNotEmpty()) return segments[0]

        return null
    }

    private suspend fun fetchVideoInfo(id: String, key: String): VideoInfo? {
        log.info("Retrieving video info from YouTube Data API for id={}", id)
        val apiUrl = "${Environment.external.youtubeApiBaseUrl}/youtube/v3/videos" +
            "?id=$id&key=$key&part=snippet" +
            "&fields=items(snippet(title,description,tags,channelTitle,publishedAt,thumbnails))"
        val raw = when (val result = webResourceRetriever.getStringResult(apiUrl)) {
            is Result.Success -> result.value
            is Result.Failure -> return null
        }
        return try {
            val root = JsonMapper.defaultMapper.readTree(raw)
            val items = root.get("items") ?: return null
            if (items.isEmpty) return null
            val snippet = items[0].get("snippet") ?: return null

            val title = snippet.get("title")?.asText() ?: return null
            val description = snippet.get("description")?.asText()
            val tags = snippet.get("tags")?.map { it.asText() }?.toSet() ?: emptySet()
            val channelTitle = snippet.get("channelTitle")?.asText()
            val publishedAt = snippet.get("publishedAt")?.asText()
            val thumbnails = snippet.get("thumbnails")

            val thumbnailUrl = thumbnails?.let {
                it.get("medium")?.get("url")?.asText()
                    ?: it.get("high")?.get("url")?.asText()
                    ?: it.get("default")?.get("url")?.asText()
            }
            val previewUrl = thumbnails?.let {
                it.get("maxres")?.get("url")?.asText()
                    ?: it.get("standard")?.get("url")?.asText()
                    ?: it.get("high")?.get("url")?.asText()
            }

            VideoInfo(title, description, tags, channelTitle, publishedAt, thumbnailUrl, previewUrl)
        } catch (e: Exception) {
            log.warn("Failed to parse YouTube API response: {}", e.message)
            null
        }
    }

    private fun fallbackThumbnailUrl() = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
    private fun fallbackPreviewUrl() = "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"

    private suspend fun generateResources(
        resourceSet: EnumSet<ResourceType>,
        info: VideoInfo?
    ): Map<ResourceType, GeneratedResource> {
        val result = mutableMapOf<ResourceType, GeneratedResource>()

        if (resourceSet.contains(ResourceType.THUMBNAIL)) {
            val imageUrl = info?.thumbnailUrl ?: videoId?.let { fallbackThumbnailUrl() }
            imageUrl?.let { generateImageResource(it, ResourceType.THUMBNAIL) }
                ?.let { result[ResourceType.THUMBNAIL] = it }
        }
        if (resourceSet.contains(ResourceType.PREVIEW)) {
            val imageUrl = info?.previewUrl ?: videoId?.let { fallbackPreviewUrl() }
            imageUrl?.let { generateImageResource(it, ResourceType.PREVIEW) }
                ?.let { result[ResourceType.PREVIEW] = it }
        }

        return result
    }

    private suspend fun generateImageResource(imageUrl: String, type: ResourceType): GeneratedResource? {
        log.info("Fetching {} for YouTube video id={}", type, videoId)
        return webResourceRetriever.getFile(imageUrl)?.let {
            val path = resourceManager.saveTempFile(url, it, type, JPG)
            GeneratedResource(type, path, JPG)
        }
    }

    override suspend fun scrapeResources(resourceSet: EnumSet<ResourceType>): List<GeneratedResource> {
        return generateResources(resourceSet, videoInfo).values.toList()
    }

    override suspend fun suggest(resourceSet: EnumSet<ResourceType>): SuggestResponse {
        val info = videoInfo ?: if (apiKey == null)
            throw SuggestionUnavailableException("YouTube suggestions not enabled")
        else
            throw SuggestionUnavailableException("YouTube suggestions unavailable")
        val linkDetails = LinkDetails(
            url = url,
            title = info.title,
            keywords = info.tags,
            description = info.description,
            author = info.channelTitle,
            published = info.publishedAt,
            image = info.thumbnailUrl ?: videoId?.let { fallbackThumbnailUrl() }
        )
        val resources = generateResources(resourceSet, info)
        return SuggestResponse(linkDetails, resources.values.toList())
    }

    private fun embedUrl(): String = "https://www.youtube.com/embed/$videoId"

    override suspend fun enrich(props: BaseProperties) {
        super.enrich(props)
        videoId?.let { props.addAttribute("embedUrl", embedUrl()) }
    }

    override fun close() {}
}
