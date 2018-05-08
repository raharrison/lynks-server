package link

import common.BaseProperties
import resource.JPG
import resource.ResourceRetriever
import util.URLUtils

class YoutubeLinkProcessor(private val retriever: ResourceRetriever) : LinkProcessor {

    private lateinit var url: String
    private lateinit var videoId: String

    override suspend fun init(url: String) {
        this.url = url
        this.videoId = URLUtils.extractQueryParams(url)["v"] ?: throw IllegalArgumentException("Invalid youtube url")
    }

    override val html: String? = null
    override var title: String = "title"
    override val resolvedUrl: String get() = url

    override fun close() {
    }

    override fun matches(url: String): Boolean = URLUtils.extractSource(url) == "youtube.com"

    private fun embedUrl(): String = "https://www.youtube.com/embed/$videoId"

    private fun downloadVideoInfo(): String? {
        val dl = "http://www.youtube.com/get_video_info?video_id=$videoId&asv=3&el=detailpage&hl=en_US"
        return retriever.getString(dl)
    }

    override suspend fun generateThumbnail(): ImageResource? {
        val dl = "http://img.youtube.com/vi/$videoId/0.jpg"
        return retriever.getFile(dl)?.let { ImageResource(it, JPG) }
    }

    override suspend fun generateScreenshot(): ImageResource? {
        val dl = "http://img.youtube.com/vi/$videoId/maxresdefault.jpg"
        return retriever.getFile(dl)?.let { ImageResource(it, JPG) }
    }

    override suspend fun printPage(): ImageResource? = null

    override suspend fun enrich(props: BaseProperties) {
        super.enrich(props)
        props.addAttribute("embedUrl", embedUrl())
        val info = downloadVideoInfo()
        info?.let {
            val params = URLUtils.extractQueryParams(info)
            title = params[title] ?: title
        }
    }
}