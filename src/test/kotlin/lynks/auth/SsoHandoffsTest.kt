package lynks.auth

import lynks.common.UserId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class SsoHandoffsTest {

    private val user = UserId("user-1")
    private val verifier = "v".repeat(43)

    @Test
    fun testRedeemsOnceWithTheVerifier() {
        val handoffs = SsoHandoffs()
        val code = handoffs.issue(user, SsoHandoffs.s256(verifier))
        assertThat(handoffs.redeem(code, verifier)).isEqualTo(user)
        assertThat(handoffs.redeem(code, verifier)).isNull()
    }

    @Test
    fun testWrongVerifierSpendsTheCode() {
        val handoffs = SsoHandoffs()
        val code = handoffs.issue(user, SsoHandoffs.s256(verifier))
        assertThat(handoffs.redeem(code, "w".repeat(43))).isNull()
        assertThat(handoffs.redeem(code, verifier)).isNull()
    }

    @Test
    fun testExpires() {
        var now = Instant.parse("2026-01-01T00:00:00Z")
        val clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId?): Clock = this
            override fun instant(): Instant = now
        }
        val handoffs = SsoHandoffs(clock)
        val code = handoffs.issue(user, SsoHandoffs.s256(verifier))
        now = now.plus(SsoHandoffs.LIFETIME)
        assertThat(handoffs.redeem(code, verifier)).isNull()
    }

    @Test
    fun testUnknownCode() {
        assertThat(SsoHandoffs().redeem("nope", verifier)).isNull()
    }

    @Test
    fun testS256MatchesRfc7636() {
        // the example in RFC 7636 appendix B
        assertThat(SsoHandoffs.s256("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
            .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
        assertThat(SsoHandoffs.isChallenge("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")).isTrue()
        assertThat(SsoHandoffs.isChallenge("short")).isFalse()
    }
}
