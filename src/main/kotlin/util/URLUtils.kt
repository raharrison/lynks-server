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

    fun extractQueryParams(uri: String): Map<String, String?> {
        val query = URI(uri).query ?: return emptyMap()
        val params = query.split("&")
        val map = linkedMapOf<String, String?>()
        for (param in params) {
            val split = param.split("=")
            when(split.size) {
                1 -> map[param] = null
                else -> map[split[0]] = split[1]
            }
        }
        return map
    }
}