package lynks.auth

import java.net.URLDecoder

// Where to send the browser after single sign-on. Only same-origin UI paths pass, so the callback is never an
// open redirect. lynks-ui's utils/returnTo.ts applies the same rules to password sign in.
object ReturnTo {

    private const val MAX_LENGTH = 2048
    private const val LOGIN_PAGE = "/login"

    fun sanitize(raw: String?, apiRootPath: String): String {
        if (raw.isNullOrEmpty() || raw.length > MAX_LENGTH) return "/"
        if (!raw.startsWith("/")) return "/"
        // browsers read a leading // or /\ as the start of another host
        if (raw.startsWith("//") || raw.startsWith("/\\")) return "/"
        if (raw.any { it.isISOControl() || it == '\\' }) return "/"
        // decoded because nginx decodes before matching /api, and dot segments because browsers resolve them
        val path = decode(raw.substringBefore('?').substringBefore('#')) ?: return "/"
        if (path.split('/').any { it == "." || it == ".." }) return "/"
        if (path == apiRootPath || path.startsWith("$apiRootPath/") || path == LOGIN_PAGE) return "/"
        return raw
    }

    private fun decode(path: String): String? = try {
        URLDecoder.decode(path, Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        null
    }
}
