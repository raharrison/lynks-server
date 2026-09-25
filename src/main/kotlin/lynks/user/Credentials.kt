package lynks.user

import lynks.common.exception.InvalidModelException

object Credentials {

    const val PASSWORD_MIN_LENGTH = 8

    // bcrypt only reads the first 72 bytes, and the library rejects anything longer
    const val PASSWORD_MAX_BYTES = 72

    fun checkPassword(password: String) {
        if (password.length < PASSWORD_MIN_LENGTH) {
            throw InvalidModelException("Password must be at least $PASSWORD_MIN_LENGTH characters")
        }
        if (password.toByteArray().size > PASSWORD_MAX_BYTES) {
            throw InvalidModelException("Password must be at most $PASSWORD_MAX_BYTES bytes")
        }
    }
}
