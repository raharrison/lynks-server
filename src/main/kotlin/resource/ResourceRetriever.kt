package resource

import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

interface ResourceRetriever {

    suspend fun getFile(location: String): ByteArray?

    suspend fun getString(location: String): String?

}

class WebResourceRetriever : ResourceRetriever {

    override suspend fun getFile(location: String): ByteArray? = try {
        val request = createRequest(location)
        log.info("Retrieving file at web location: {}", location)
        val future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
        future.await().let {
            log.info("Retrieved status code {} from {}", it.statusCode(), location)
            if (it.statusCode() == 200) it.body()
            else null
        }
    } catch (e: Exception) {
        log.error("Error retrieving file at web location: {}", location, e)
        null
    }

    override suspend fun getString(location: String): String? = try {
        val request = createRequest(location)
        log.info("Retrieving data at web location: {}", location)
        val future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        future.await().let {
            log.info("Retrieved status code: {} from: {}", it.statusCode(), location)
            if (it.statusCode() == 200) it.body()
            else null
        }
    } catch (e: Exception) {
        log.error("Error retrieving data at web location: {}", location, e)
        null
    }

    private fun createRequest(location: String): HttpRequest {
        return HttpRequest.newBuilder(URI.create(location))
                .GET()
                .build()
    }

    companion object {
        private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
        private val log = LoggerFactory.getLogger(WebResourceRetriever::class.java)
    }

}