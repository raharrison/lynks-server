package lynks.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

// Capacitor's WebView origins, https on Android and capacitor on iOS
private val APP_ORIGINS = setOf("https://localhost", "capacitor://localhost")

// The app calls the API cross-origin with a bearer token. Credentials are never allowed, so no cookie crosses.
// Any other origin is left alone rather than refused, since the browser UI's same-origin writes carry an Origin too.
val AppCors = createApplicationPlugin("AppCors") {
    onCall { call ->
        val origin = call.request.headers[HttpHeaders.Origin] ?: return@onCall
        if (origin !in APP_ORIGINS) return@onCall
        call.response.header(HttpHeaders.AccessControlAllowOrigin, origin)
        call.response.header(HttpHeaders.Vary, HttpHeaders.Origin)
        if (call.request.httpMethod == HttpMethod.Options && call.request.headers.contains(HttpHeaders.AccessControlRequestMethod)) {
            call.response.header(HttpHeaders.AccessControlAllowMethods, "GET, POST, PUT, PATCH, DELETE")
            call.response.header(HttpHeaders.AccessControlAllowHeaders, "Authorization, Content-Type")
            call.response.header(HttpHeaders.AccessControlMaxAge, "600")
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
