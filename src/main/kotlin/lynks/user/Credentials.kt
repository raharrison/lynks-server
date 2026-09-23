package lynks.user

import lynks.common.exception.InvalidModelException

object Credentials {

    private val usernamePattern = Regex("^[A-Za-z0-9_.-]{3,$USERNAME_MAX_LENGTH}$")

    const val PASSWORD_MIN_LENGTH = 8

    // bcrypt only reads the first 72 bytes, and the library rejects anything longer
    const val PASSWORD_MAX_BYTES = 72

    fun checkUsername(username: String) {
        if (!usernamePattern.matches(username)) {
            throw InvalidModelException(
                "Username must be 3 to $USERNAME_MAX_LENGTH characters of letters, digits, '.', '_' or '-'"
            )
        }
    }

    fun checkPassword(password: String) {
        if (password.length < PASSWORD_MIN_LENGTH) {
            throw InvalidModelException("Password must be at least $PASSWORD_MIN_LENGTH characters")
        }
        if (password.toByteArray().size > PASSWORD_MAX_BYTES) {
            throw InvalidModelException("Password must be at most $PASSWORD_MAX_BYTES bytes")
        }
    }
}
