package lynks.auth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PendingLoginTest {

    @Test
    fun testRoundTrip() {
        val login = PendingLogin("state-1", "nonce-1", "verifier-1", "/entries/abc?tag=a.b&x=%2F#frag")
        val encoded = login.encode()
        assertThat(encoded).matches("[A-Za-z0-9._-]+")
        assertThat(PendingLogin.decode(encoded)).isEqualTo(login)
    }

    @Test
    fun testMalformedIsNull() {
        listOf("", "a.b.c", "a.b.c.d.e", "a..c.Lw", "a.b.c.!!!").forEach {
            assertThat(PendingLogin.decode(it)).describedAs(it).isNull()
        }
    }
}
