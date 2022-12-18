package lynks.user

import dev.turingcomplete.kotlinonetimepassword.GoogleAuthenticator
import lynks.util.loggerFor
import org.apache.commons.lang3.StringUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class TwoFactorService {

    private val log = loggerFor<TwoFactorService>()

    fun validateTotp(username: String, code: String?): AuthResult {
        val secret = getTwoFactorSecret(username)
            ?: return if(StringUtils.isEmpty(code)) AuthResult.SUCCESS else AuthResult.INVALID_CREDENTIALS
        if(StringUtils.isEmpty(code)) {
            // 2fa required but no code provided
            return AuthResult.TOTP_REQUIRED
        }
        val gen = GoogleAuthenticator(secret.toByteArray())
        log.info("Validating totp code for user={}", username)
        return if(gen.generate() == code) AuthResult.SUCCESS else AuthResult.INVALID_CREDENTIALS
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
