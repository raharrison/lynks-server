package util

import java.net.URI
import java.net.URLDecoder

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
        val query = URI(uri).query ?: uri
        val params = query.split("&")
        val map = linkedMapOf<String, String?>()
        for (param in params) {
            if(param == uri) continue
            val split = param.split("=")
            when(split.size) {
                1 -> map[param] = null
                else -> map[split[0]] = URLDecoder.decode(split[1], "UTF-8")
            }
        }
        return map
    }

    fun isValidUrl(url: String): Boolean = try {
        url.contains('.') && extractSource(url).isNotEmpty()
    } catch (e: Exception) {
        false
    }
}