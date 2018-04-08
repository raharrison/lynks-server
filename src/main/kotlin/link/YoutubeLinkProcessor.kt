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
    override val title: String = "title"
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

    override fun generateThumbnail(): ImageResource? {
        val dl = "http://img.youtube.com/vi/$videoId/0.jpg"
        return imageGet(dl)?.let { ImageResource(it, JPG) }
    }

    override fun generateScreenshot(): ImageResource? {
        val dl = "http://img.youtube.com/vi/$videoId/maxresdefault.jpg"
        return imageGet(dl)?.let { ImageResource(it, JPG) }
    }

    override fun enrich(props: BaseProperties) {
    }
}