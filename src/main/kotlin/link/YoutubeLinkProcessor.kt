package link

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import common.BaseProperties
import kotlinx.coroutines.runBlocking
import link.extract.LinkContent
import resource.JPG
import resource.ResourceRetriever
import task.YoutubeDlTask
import util.JsonMapper
import util.URLUtils
import util.loggerFor
import java.net.URLEncoder

private val log = loggerFor<YoutubeLinkProcessor>()

class YoutubeLinkProcessor(private val url: String, private val retriever: ResourceRetriever) : LinkProcessor {

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
        val params = URLUtils.extractQueryParams(raw)
        if (params.containsKey("player_response")) {
            val playerResponse = params["player_response"]
            val playerResponseJson = JsonMapper.defaultMapper.readTree(playerResponse)
            if ("error".equals(playerResponseJson["playabilityStatus"]["status"].asText("error"), true)) {
                return null
            }
            return playerResponseJson["videoDetails"]
        }
        return null
    }

    override suspend fun extractLinkContent(): LinkContent {
        val title = videoInfo.value?.get("title")?.asText() ?: ""
        val keywords = extractKeywords()
        return LinkContent(title = title, keywords = keywords)
    }

    private fun extractKeywords(): Set<String> {
        val keywords = videoInfo.value?.get("keywords")
        if (keywords is ArrayNode) {
            return keywords.map { it.textValue() }.toSet()
        }
        return emptySet()
    }

    override val html: String? = null

    override val resolvedUrl: String get() = url

    override fun close() {
    }

    override fun matches(): Boolean = URLUtils.extractSource(url) == "youtube.com"

    private fun embedUrl(): String = "https://www.youtube.com/embed/${extractVideoId()}"

    private suspend fun downloadVideoInfo(): String? {
        log.info("Retrieving video info for Youtube video id={}", videoId)
        val eurl = URLEncoder.encode("https://youtube.googleapis.com/v/$videoId", "UTF-8")
        val url = "https://youtube.com/get_video_info?video_id=$videoId&el=embedded&eurl=$eurl&hl=en"
        return retriever.getString(url)
    }

    override suspend fun generateThumbnail(): ImageResource? {
        log.info("Capturing thumbnail for Youtube video videoId={}", videoId)
        val dl = "https://img.youtube.com/vi/$videoId/mqdefault.jpg"
        return retriever.getFile(dl)?.let { ImageResource(it, JPG) }
    }

    override suspend fun generateScreenshot(): ImageResource? {
        log.info("Capturing screenshot for Youtube video videoId={}", videoId)
        val dl = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
        return retriever.getFile(dl)?.let { ImageResource(it, JPG) }
    }

    override suspend fun printPage(): ImageResource? = null

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
    }
}