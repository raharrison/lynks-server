package lynks.auth

import java.util.*

// Kept in the browser's own state cookie rather than on the server, so starting a login leaves nothing to flood.
// It needs no signature: only that browser can set the cookie, and altering it can only spoil its own login.
data class PendingLogin(
    val state: String,
    val nonce: String,
    val codeVerifier: String,
    val returnTo: String,
    // set when the app started the login, which then gets a handoff code instead of a session cookie
    val appChallenge: String? = null
) {

    fun encode(): String =
        listOfNotNull(state, nonce, codeVerifier, base64.encodeToString(returnTo.toByteArray()), appChallenge).joinToString(".")

    companion object {
        private val base64 = Base64.getUrlEncoder().withoutPadding()

        // so a callback that lost its cookie still knows to answer the app rather than the login page
        const val APP_STATE_PREFIX = "app-"

        fun isAppState(state: String?): Boolean = state?.startsWith(APP_STATE_PREFIX) == true

        fun decode(value: String): PendingLogin? {
            val parts = value.split('.')
            if (parts.size !in 4..5 || parts.any { it.isEmpty() }) return null
            val appChallenge = parts.getOrNull(4)
            if (appChallenge != null && !SsoHandoffs.isChallenge(appChallenge)) return null
            val returnTo = try {
                String(Base64.getUrlDecoder().decode(parts[3]))
            } catch (_: IllegalArgumentException) {
                return null
            }
            return PendingLogin(parts[0], parts[1], parts[2], returnTo, appChallenge)
        }
    }
}
