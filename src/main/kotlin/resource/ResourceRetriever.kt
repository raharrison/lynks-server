package resource

import common.exception.ExecutionException
import kotlinx.coroutines.future.await
import util.Result
import util.loggerFor
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

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
        future.await().let {
            log.info("Retrieved status code: {} from: {}", it.statusCode(), location)
            if (it.statusCode() == 200) Result.Success(it.body())
            else Result.Failure(ExecutionException("Bad response code from remote data request", it.statusCode()))
        }
    } catch (e: Exception) {
        log.error("Error retrieving data at web location: {}", location, e)
        Result.Failure(ExecutionException("Error occurred retrieving remote data: " + e.message))
    }

    private fun createGetRequest(location: String): HttpRequest {
        return HttpRequest.newBuilder(URI.create(location))
            .GET()
            .build()
    }

    companion object {
        private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
        private val log = loggerFor<WebResourceRetriever>()
    }

}
