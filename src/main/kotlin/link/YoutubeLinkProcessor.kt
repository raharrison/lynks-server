package link

import common.BaseProperties
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import resource.JPG
import resource.ResourceRetriever
import task.YoutubeDlTask
import util.JsonMapper
import util.URLUtils
import java.net.URLEncoder

private val log = LoggerFactory.getLogger(YoutubeLinkProcessor::class.java)

class YoutubeLinkProcessor(private val retriever: ResourceRetriever) : LinkProcessor {

    private lateinit var url: String
    private lateinit var videoId: String

    override suspend fun init(url: String) {
        this.url = url
        this.videoId = URLUtils.extractQueryParams(url)["v"] ?: throw IllegalArgumentException("Invalid youtube url")
    }

    override val html: String? = null
    override val content: String? = null

    override val title: String get() = runBlocking {
        downloadVideoInfo()?.let {
            val params = URLUtils.extractQueryParams(it)
            if(params.containsKey("player_response")) {
                val playerResponse = params["player_response"]
                val playerResponseJson = JsonMapper.defaultMapper.readTree(playerResponse)
                if ("error".equals(playerResponseJson["playabilityStatus"]["status"].asText("error"), true)) {
                    return@runBlocking ""
                }
                return@runBlocking playerResponseJson["videoDetails"]["title"].asText()
            } else ""
        }
        ""
    }
    override val resolvedUrl: String get() = url

    override fun close() {
    }

    override fun matches(url: String): Boolean = URLUtils.extractSource(url) == "youtube.com"

    private fun embedUrl(): String = "https://www.youtube.com/embed/$videoId"

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
        props.addTask("Download Video (max 1080p)", YoutubeDlTask.build(url, YoutubeDlTask.YoutubeDlDownload.BEST_VIDEO_TRANSCODE))
    }
}