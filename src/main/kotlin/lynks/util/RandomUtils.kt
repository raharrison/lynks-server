package lynks.util

import java.nio.ByteBuffer
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

    fun generateUuid64(): String {
        val uuid = UUID.randomUUID()
        val bytes = ByteBuffer.wrap(ByteArray(16))
        bytes.putLong(uuid.mostSignificantBits)
        bytes.putLong(uuid.leastSignificantBits)
        return encoder.encodeToString(bytes.array())
    }

}
