package lynks.user

import dev.turingcomplete.kotlinonetimepassword.GoogleAuthenticator
import lynks.util.loggerFor
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class TwoFactorService {

    private val log = loggerFor<TwoFactorService>()

    fun validateTotp(username: String, code: String): Boolean {
        val secret = getTwoFactorSecret(username) ?: return false
        val gen = GoogleAuthenticator(secret.toByteArray())
        log.info("Validating totp code for user={}", username)
        return gen.generate() == code
    }

    fun getTwoFactorSecret(username: String): String? = transaction {
        Users.slice(Users.totp)
            .select { Users.username eq username and Users.activated }
            .map { it[Users.totp] }
            .singleOrNull()
    }

    fun updateTwoFactorEnabled(username: String, enabled: Boolean): Boolean = transaction {
        val totp = if (enabled) String(GoogleAuthenticator.createRandomSecretAsByteArray()) else null
        log.info("Updating two factor settings for user={} to {}", username, enabled)
        val updated = Users.update({ Users.username eq username and Users.activated }) {
            it[Users.totp] = totp
            it[dateUpdated] = System.currentTimeMillis()
        }
        updated > 0
    }
}
