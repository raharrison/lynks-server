package lynks.user

import dev.turingcomplete.kotlinonetimepassword.GoogleAuthenticator
import lynks.common.UserId
import lynks.util.loggerFor
import org.apache.commons.lang3.StringUtils
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

class TwoFactorService {

    private val log = loggerFor<TwoFactorService>()

    fun validateTotp(userId: UserId, code: String?): AuthResult {
        val secret = getTwoFactorSecret(userId)
            ?: return if(StringUtils.isEmpty(code)) AuthResult.SUCCESS else AuthResult.INVALID_CREDENTIALS
        if(StringUtils.isEmpty(code)) {
            // 2fa required but no code provided
            return AuthResult.TOTP_REQUIRED
        }
        val gen = GoogleAuthenticator(secret.toByteArray())
        log.info("Validating totp code for user={}", userId)
        val now = System.currentTimeMillis()
        val window = 30_000L
        val valid = gen.generate(Date(now - window)) == code || gen.generate(Date(now)) == code || gen.generate(Date(now + window)) == code
        return if (valid) AuthResult.SUCCESS else AuthResult.INVALID_CREDENTIALS
    }

    fun getTwoFactorSecret(userId: UserId): String? = transaction {
        Users.select(Users.totp)
            .where { (Users.id eq userId.value) and Users.activated }
            .map { it[Users.totp] }
            .singleOrNull()
    }

    fun updateTwoFactorEnabled(userId: UserId, enabled: Boolean): Boolean = transaction {
        val totp = if (enabled) String(GoogleAuthenticator.createRandomSecretAsByteArray()) else null
        log.info("Updating two factor settings for user={} to {}", userId, enabled)
        val updated = Users.update({ (Users.id eq userId.value) and Users.activated }) {
            it[Users.totp] = totp
            it[dateUpdated] = OffsetDateTime.now(ZoneOffset.UTC)
        }
        updated > 0
    }
}
