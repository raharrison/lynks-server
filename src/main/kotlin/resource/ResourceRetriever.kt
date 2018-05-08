package resource

import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result

interface ResourceRetriever {

    fun getFile(location: String): ByteArray?

    fun getString(location: String): String?

}

class WebResourceRetriever : ResourceRetriever {

    override fun getString(location: String): String? = try {
        val result = location.httpGet().responseString().third
        when (result) {
            is Result.Success -> result.get()
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    override fun getFile(location: String): ByteArray? = try {
        val result = location.httpGet().response().third
        when (result) {
            is Result.Success -> result.get()
            else -> null
        }
    } catch (e: Exception) {
        null
    }

}