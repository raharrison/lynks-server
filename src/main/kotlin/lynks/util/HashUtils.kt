package lynks.util

import at.favre.lib.crypto.bcrypt.BCrypt
import java.security.MessageDigest

object HashUtils {

    private const val HEX_CHARS = "0123456789ABCDEF"

    fun sha1Hash(input: String): String = hexDigest("SHA-1", input)

    fun sha256Hash(input: String): String = hexDigest("SHA-256", input)

    private fun hexDigest(algorithm: String, input: String): String {
        val bytes = MessageDigest.getInstance(algorithm).digest(input.toByteArray())
        val result = StringBuilder(bytes.size * 2)

        bytes.forEach {
            val i = it.toInt()
            result.append(HEX_CHARS[i shr 4 and 0x0f])
            result.append(HEX_CHARS[i and 0x0f])
        }
        return result.toString()
    }

    fun bcryptHash(str: String): String {
        return BCrypt.withDefaults().hashToString(12, str.toCharArray())
    }

    fun verifyBcryptHash(raw: CharArray, hash: CharArray): Boolean {
        return BCrypt.verifyer().verify(raw, hash).verified
    }

}
