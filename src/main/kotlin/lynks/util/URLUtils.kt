package lynks.util

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object URLUtils {

    fun extractSource(url: String): String {
        var uri = url.lowercase()
        if (!uri.startsWith("http://") and !uri.startsWith("https://")) {
            uri = "https://$url"
        }
        val host = URI(uri).host
        if (host == null) {
            throw IllegalArgumentException("URL is not valid")
        } else {
            return if (host.startsWith("www.")) host.substring(4).lowercase() else host.lowercase()
        }
    }

    fun extractQueryParams(uri: String): Map<String, String?> {
        val query = try {
            URI(uri).query ?: uri
        } catch (_: Exception) {
            uri
        }
        val params = query.split("&")
        val map = linkedMapOf<String, String?>()
        for (param in params) {
            if (param == uri) continue
            val split = param.split("=")
            when (split.size) {
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

    fun encode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
    }

    fun ensureUrlProtocol(url: String): String {
        if (!url.startsWith("http://") and !url.startsWith("https://")) {
            return "https://$url"
        }
        return url
    }
}
