package link

import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result
import common.BaseProperties
import resource.JPG
import util.URLUtils

class YoutubeLinkProcessor: LinkProcessor {

    private lateinit var url: String
    private lateinit var videoId: String

    override fun init(url: String) {
        this.url = url
        this.videoId = URLUtils.extractQueryParams(url)["v"] ?: throw IllegalArgumentException("Invalid youtube url")
    }

    override val html: String? = null
    override var title: String = "title"
    override val resolvedUrl: String get() = url

    override fun close() {
    }

    override fun matches(url: String): Boolean = URLUtils.extractSource(url) == "youtube.com"

    private fun imageGet(url: String): ByteArray? {
        val result = url.httpGet().response().third
        return when(result) {
            is Result.Success -> result.get()
            else -> null
        }
    }

    private fun embedUrl(): String = "https://www.youtube.com/embed/$videoId"

    private fun downloadVideoInfo(): String {
        val dl = "http://www.youtube.com/get_video_info?video_id=$videoId&asv=3&el=detailpage&hl=en_US"
        return dl.httpGet().responseString().third.get()
    }

    override fun generateThumbnail(): ImageResource? {
        val dl = "http://img.youtube.com/vi/$videoId/0.jpg"
        return imageGet(dl)?.let { ImageResource(it, JPG) }
    }

    override fun generateScreenshot(): ImageResource? {
        val dl = "http://img.youtube.com/vi/$videoId/maxresdefault.jpg"
        return imageGet(dl)?.let { ImageResource(it, JPG) }
    }

    override fun printPage(): ImageResource? = null

    override fun enrich(props: BaseProperties) {
        props.addAttribute("embedUrl", embedUrl())
        val info = downloadVideoInfo()
        val params = URLUtils.extractQueryParams(info)
        title = params[title] ?: title
    }
}