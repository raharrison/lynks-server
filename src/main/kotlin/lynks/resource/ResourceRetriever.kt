package lynks.resource

import io.ktor.http.*
import kotlinx.coroutines.future.await
import lynks.common.MDC_REQUEST_ID
import lynks.common.exception.ExecutionException
import lynks.util.JsonMapper
import lynks.util.Result
import lynks.util.URLUtils
import lynks.util.loggerFor
import org.apache.commons.lang3.StringUtils
import org.slf4j.MDC
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
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
        log.info("Retrieving file at web location: {}", location)
        val future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
        future.await().let {
            log.info("Retrieved status code {} from {}", it.statusCode(), location)
            if (it.statusCode() == 200) Result.Success(it.body())
            else Result.Failure(ExecutionException("Bad response code from remote data request", it.statusCode()))
        }
    } catch (e: Exception) {
        log.error("Error retrieving file at web location: {}", location, e)
        Result.Failure(ExecutionException("Error occurred retrieving remote data: " + e.message))
    }

    override suspend fun getStringResult(location: String): Result<String, ExecutionException> = try {
        val request = createGetRequest(location)
        log.info("Retrieving data at web location: {}", location)
        val future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        handleAsyncResponseAsResult(location, future)
    } catch (e: Exception) {
        log.error("Error retrieving data at web location: {}", location, e)
        Result.Failure(ExecutionException("Error occurred retrieving remote data: " + e.message))
    }

    suspend fun postStringResult(location: String, body: Any): Result<String, ExecutionException> = try {
        val json = if(body is String) body else JsonMapper.defaultMapper.writeValueAsString(body)
        val request = createPostRequest(location, json, ContentType.Application.Json)
        log.info("Posting data to web location: {}", location)
        val future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        handleAsyncResponseAsResult(location, future)
    } catch (e: Exception) {
        log.error("Error posting data to web location: {}", location, e)
        Result.Failure(ExecutionException("Error occurred posting to endpoint: " + e.message))
    }

    suspend fun postFormStringResult(location: String, params: Map<String, String>): Result<String, ExecutionException> = try {
        val encodedParams = params
            .map { entry -> entry.key + "=" + URLUtils.encode(entry.value) }
            .joinToString("&")
        val request = createPostRequest(location, encodedParams, ContentType.Application.FormUrlEncoded)
        log.info("Posting form data to web location: {}", location)
        val future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        handleAsyncResponseAsResult(location, future)
    } catch (e: Exception) {
        log.error("Error posting form data to web location: {}", location, e)
        Result.Failure(ExecutionException("Error occurred posting to endpoint: " + e.message))
    }

    private suspend fun handleAsyncResponseAsResult(
        location: String,
        future: CompletableFuture<HttpResponse<String>>
    ): Result<String, ExecutionException> {
        return future.await().let {
            log.info("Retrieved status code: {} from: {}", it.statusCode(), location)
            if (it.statusCode() == 200) Result.Success(it.body())
            else Result.Failure(ExecutionException("Bad response code from remote data request: " + it.body(), it.statusCode()))
        }
    }

    private fun createGetRequest(location: String): HttpRequest {
        return createBaseRequest(location)
            .GET()
            .build()
    }

    private fun createPostRequest(location: String, content: String, contentType: ContentType): HttpRequest {
        return createBaseRequest(location)
            .header(HttpHeaders.ContentType, contentType.toString())
            .POST(HttpRequest.BodyPublishers.ofString(content))
            .build()
    }

    private fun createBaseRequest(location: String): HttpRequest.Builder {
        val builder = HttpRequest.newBuilder(URI.create(location))
        val requestId = MDC.get(MDC_REQUEST_ID)
        if (StringUtils.isNotEmpty(requestId)) {
            builder.setHeader(HttpHeaders.XRequestId, requestId)
        }
        return builder
    }

    companion object {
        private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
        private val log = loggerFor<WebResourceRetriever>()
    }

}
