package lynks.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.util.date.*
import java.time.Duration

class AuthCookies(private val secure: Boolean, private val sessionMaxAge: Duration) {

    // __Host- stops a sibling subdomain setting or replacing a cookie, but browsers only accept it with Secure
    private val prefix = if (secure) "__Host-" else ""
    val session = "${prefix}lynks_session"
    val state = "${prefix}lynks_oidc"

    fun sessionToken(call: ApplicationCall): String? = call.request.cookies[session, CookieEncoding.RAW]

    fun loginState(call: ApplicationCall): PendingLogin? =
        call.request.cookies[state, CookieEncoding.RAW]?.let { PendingLogin.decode(it) }

    fun setSession(call: ApplicationCall, token: String) = append(call, session, token, sessionMaxAge.seconds)

    fun clearSession(call: ApplicationCall) = expire(call, session)

    fun setLoginState(call: ApplicationCall, login: PendingLogin) = append(call, state, login.encode(), STATE_MAX_AGE.seconds)

    fun clearLoginState(call: ApplicationCall) = expire(call, state)

    private fun append(call: ApplicationCall, name: String, value: String, maxAgeSeconds: Long) {
        call.response.cookies.append(cookie(name, value, maxAgeSeconds.toInt(), null))
    }

    // the attributes have to match the original, since browsers ignore a __Host- cookie without Secure
    private fun expire(call: ApplicationCall, name: String) {
        call.response.cookies.append(cookie(name, "", 0, GMTDate.START))
    }

    private fun cookie(name: String, value: String, maxAge: Int, expires: GMTDate?) = Cookie(
        name = name,
        value = value,
        encoding = CookieEncoding.RAW,
        maxAge = maxAge,
        expires = expires,
        path = "/",
        secure = secure,
        httpOnly = true,
        extensions = mapOf("SameSite" to "Lax")
    )

    companion object {
        val STATE_MAX_AGE: Duration = Duration.ofMinutes(10)
    }
}
