package lynks.util

import java.security.SecureRandom
import java.util.*

object RandomUtils {

    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    fun generateUid(): String {
        val bytes = ByteArray(10)
        random.nextBytes(bytes)
        return encoder.encodeToString(bytes)
    }

}
