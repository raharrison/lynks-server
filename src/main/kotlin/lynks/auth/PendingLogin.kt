package lynks.auth

import java.util.*

// Kept in the browser's own state cookie rather than on the server, so starting a login leaves nothing to flood.
// It needs no signature: only that browser can set the cookie, and altering it can only spoil its own login.
data class PendingLogin(
    val state: String,
    val nonce: String,
    val codeVerifier: String,
    val returnTo: String
) {

    fun encode(): String = listOf(state, nonce, codeVerifier, base64.encodeToString(returnTo.toByteArray())).joinToString(".")

    companion object {
        private val base64 = Base64.getUrlEncoder().withoutPadding()

        fun decode(value: String): PendingLogin? {
            val parts = value.split('.')
            if (parts.size != 4 || parts.any { it.isEmpty() }) return null
            val returnTo = try {
                String(Base64.getUrlDecoder().decode(parts[3]))
            } catch (_: IllegalArgumentException) {
                return null
            }
            return PendingLogin(parts[0], parts[1], parts[2], returnTo)
        }
    }
}
