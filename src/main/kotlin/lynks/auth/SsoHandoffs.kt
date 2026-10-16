package lynks.auth

import lynks.common.UserId
import lynks.util.HashUtils
import lynks.util.RandomUtils
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

// Carries an app's single sign-on from the browser tab that finished it back into the app. The code arrives through a
// custom url scheme any other app could also claim, so it is worthless without the verifier the app kept (RFC 8252).
// In memory, since a code lives for seconds and one lost to a restart costs a retry.
class SsoHandoffs(private val clock: Clock = Clock.systemUTC()) {

    private data class Handoff(val userId: UserId, val challenge: String, val expiresAt: Instant)

    private val pending = ConcurrentHashMap<String, Handoff>()

    fun issue(userId: UserId, challenge: String): String {
        val now = clock.instant()
        pending.values.removeIf { it.expiresAt <= now }
        val code = RandomUtils.generateToken()
        pending[HashUtils.sha256Hash(code)] = Handoff(userId, challenge, now.plus(LIFETIME))
        return code
    }

    // single use whatever the outcome, so a wrong verifier spends the code rather than inviting another guess
    fun redeem(code: String, verifier: String): UserId? {
        val handoff = pending.remove(HashUtils.sha256Hash(code)) ?: return null
        if (handoff.expiresAt <= clock.instant()) return null
        if (!MessageDigest.isEqual(s256(verifier).toByteArray(), handoff.challenge.toByteArray())) return null
        return handoff.userId
    }

    companion object {
        val LIFETIME: Duration = Duration.ofMinutes(2)

        private val CHALLENGE = Regex("[A-Za-z0-9_-]{43}")
        private val base64 = Base64.getUrlEncoder().withoutPadding()

        fun isChallenge(value: String): Boolean = CHALLENGE.matches(value)

        fun s256(verifier: String): String =
            base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
    }
}
