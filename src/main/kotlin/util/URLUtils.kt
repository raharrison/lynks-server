package util

import java.net.URI

object URLUtils {
    fun extractSource(url: String): String {
        var uri = url.toLowerCase()
        if (!uri.startsWith("http://") and !uri.startsWith("https://")) {
            uri = "http://$url"
        }
        val host = URI(uri).host
        if (host == null) {
            throw IllegalArgumentException("URL is not valid")
        } else {
            return if (host.startsWith("www.")) host.substring(4) else host
        }
    }
}