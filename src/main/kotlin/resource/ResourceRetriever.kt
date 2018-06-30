package resource

import com.github.kittinunf.fuel.core.*
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result
import kotlinx.coroutines.experimental.suspendCancellableCoroutine
import java.nio.charset.Charset

interface ResourceRetriever {

    suspend fun getFile(location: String): ByteArray?

    suspend fun getString(location: String): String?

}

private suspend fun <T : Any, U : Deserializable<T>> Request.await(
        deserializable: U
): Triple<Request, Response, Result<T, FuelError>> =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                if (continuation.isCancelled) {
                    continuation.cancel()
                }
            }

            response(deserializable) { request: Request, response: Response, result: Result<T, FuelError> ->
                result.fold({
                    continuation.resume(Triple(request, response, result))
                }, {
                    continuation.resumeWithException(it.exception)
                })
            }
        }

suspend fun Request.awaitResponse(): Triple<Request, Response, Result<ByteArray, FuelError>> =
        await(Request.byteArrayDeserializer())

suspend fun Request.awaitString(
        charset: Charset = Charsets.UTF_8
): Triple<Request, Response, Result<String, FuelError>> = await(Request.stringDeserializer(charset))


class WebResourceRetriever : ResourceRetriever {

    override suspend fun getString(location: String): String? = try {
        val result = location.httpGet().awaitString().third
        when (result) {
            is Result.Success -> result.get()
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    override suspend fun getFile(location: String): ByteArray? = try {
        val result = location.httpGet().awaitResponse().third
        when (result) {
            is Result.Success -> result.get()
            else -> null
        }
    } catch (e: Exception) {
        null
    }

}