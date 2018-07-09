package resource

import awaitByteArrayResult
import awaitStringResult
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result

interface ResourceRetriever {

    suspend fun getFile(location: String): ByteArray?

    suspend fun getString(location: String): String?

}

class WebResourceRetriever : ResourceRetriever {

    override suspend fun getString(location: String): String? = try {
        val result = location.httpGet().awaitStringResult()
        when (result) {
            is Result.Success -> result.get()
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    override suspend fun getFile(location: String): ByteArray? = try {
        val result = location.httpGet().awaitByteArrayResult()
        when (result) {
            is Result.Success -> result.get()
            else -> null
        }
    } catch (e: Exception) {
        null
    }

}