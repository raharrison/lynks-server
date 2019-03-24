package resource

import kotlinx.coroutines.future.await
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
        val future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
        future.await().let {
            if (it.statusCode() == 200) it.body()
            else null
        }
    } catch (e: Exception) {
        null
    }

    override suspend fun getString(location: String): String? = try {
        val request = createRequest(location)
        val future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        future.await().let {
            if (it.statusCode() == 200) it.body()
            else null
        }
    } catch (e: Exception) {
        null
    }

    private fun createRequest(location: String): HttpRequest {
        return HttpRequest.newBuilder(URI.create(location))
                .GET()
                .build()
    }

    companion object {
        private val client = HttpClient.newHttpClient()
    }

}