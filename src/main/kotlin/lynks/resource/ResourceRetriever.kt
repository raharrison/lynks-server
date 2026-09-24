package lynks.resource

import io.ktor.http.*
import kotlinx.coroutines.future.await
import lynks.common.Environment
import lynks.common.MDC_REQUEST_ID
import lynks.common.exception.ExecutionException
import lynks.util.JsonMapper
import lynks.util.Result
import lynks.util.URLUtils
import lynks.util.loggerFor
import org.slf4j.MDC
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

interface ResourceRetriever {

    suspend fun getFileResult(location: String): Result<ByteArray, ExecutionException>

    suspend fun getFile(location: String): ByteArray? {
        val result = getFileResult(location)
        if (result is Result.Success) {
            return result.value
        }
        return null
    }

    suspend fun getStringResult(location: String): Result<String, ExecutionException>

    suspend fun getString(location: String): String? {
        val result = getStringResult(location)
        if (result is Result.Success) {
            return result.value
        }
        return null
    }

}

class WebResourceRetriever : ResourceRetriever {

    override suspend fun getFileResult(location: String): Result<ByteArray, ExecutionException> = try {
        val request = createGetRequest(location)
        log.info("Retrieving file at web location: {}", redact(location))
        val future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
        future.await().let {
            log.info("Retrieved status code {} from {}", it.statusCode(), redact(location))
            if (it.statusCode() == 200) Result.Success(it.body())
            else Result.Failure(ExecutionException("Bad response code from remote data request", it.statusCode()))
        }
    } catch (e: Exception) {
        log.error("Error retrieving file at web location: {}", redact(location), e)
        Result.Failure(ExecutionException("Error occurred retrieving remote data: " + e.message))
    }

    override suspend fun getStringResult(location: String): Result<String, ExecutionException> = try {
        val request = createGetRequest(location)
        log.info("Retrieving data at web location: {}", redact(location))
        val future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        handleAsyncResponseAsResult(location, future)
    } catch (e: Exception) {
        log.error("Error retrieving data at web location: {}", redact(location), e)
        Result.Failure(ExecutionException("Error occurred retrieving remote data: " + e.message))
    }

    suspend fun postStringResult(
        location: String,
        body: Any,
        timeout: Duration = REQUEST_TIMEOUT
    ): Result<String, ExecutionException> = try {
        val json = if(body is String) body else JsonMapper.defaultMapper.writeValueAsString(body)
        val request = createPostRequest(location, json, ContentType.Application.Json, timeout)
        log.info("Posting data to web location: {}", redact(location))
        val future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        handleAsyncResponseAsResult(location, future)
    } catch (e: Exception) {
        log.error("Error posting data to web location: {}", redact(location), e)
        Result.Failure(ExecutionException("Error occurred posting to endpoint: " + e.message))
    }

    suspend fun postFormStringResult(location: String, params: Map<String, String>): Result<String, ExecutionException> = try {
        val encodedParams = params
            .map { entry -> entry.key + "=" + URLUtils.encode(entry.value) }
            .joinToString("&")
        val request = createPostRequest(location, encodedParams, ContentType.Application.FormUrlEncoded)
        log.info("Posting form data to web location: {}", redact(location))
        val future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        handleAsyncResponseAsResult(location, future)
    } catch (e: Exception) {
        log.error("Error posting form data to web location: {}", redact(location), e)
        Result.Failure(ExecutionException("Error occurred posting to endpoint: " + e.message))
    }

    private suspend fun handleAsyncResponseAsResult(
        location: String,
        future: CompletableFuture<HttpResponse<String>>
    ): Result<String, ExecutionException> {
        return future.await().let {
            log.info("Retrieved status code: {} from: {}", it.statusCode(), redact(location))
            if (it.statusCode() == 200) Result.Success(it.body())
            else Result.Failure(ExecutionException("Bad response code from remote data request: " + it.body(), it.statusCode()))
        }
    }

    private fun createGetRequest(location: String): HttpRequest {
        return createBaseRequest(location)
            .GET()
            .build()
    }

    private fun createPostRequest(
        location: String,
        content: String,
        contentType: ContentType,
        timeout: Duration = REQUEST_TIMEOUT
    ): HttpRequest {
        return createBaseRequest(location, timeout)
            .header(HttpHeaders.ContentType, contentType.toString())
            .POST(HttpRequest.BodyPublishers.ofString(content))
            .build()
    }

    private fun createBaseRequest(location: String, timeout: Duration = REQUEST_TIMEOUT): HttpRequest.Builder {
        val builder = HttpRequest.newBuilder(URI.create(location))
            .timeout(timeout)
            .header("User-Agent", USER_AGENT)
        val requestId = MDC.get(MDC_REQUEST_ID)
        if (!requestId.isNullOrEmpty()) {
            builder.setHeader(HttpHeaders.XRequestId, requestId)
        }
        return builder
    }

    private val secrets = listOfNotNull(Environment.external.youtubeApiKey).filter { it.isNotBlank() }

    // jolt tokens belong to users, so they are masked by position rather than listed up front
    private val joltToken = Regex("/inbound/[^/?#]+")

    private fun redact(location: String): String = secrets
        .fold(location) { acc, secret -> acc.replace(secret, "***") }
        .replace(joltToken, "/inbound/***")

    companion object {
        val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(30)

        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0"

        // The default h2c upgrade on plain http loses POST bodies on some servers, such as the Jetty in WireMock
        private val client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build()
        private val log = loggerFor<WebResourceRetriever>()
    }

}
