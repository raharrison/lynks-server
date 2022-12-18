package lynks.user

import dev.turingcomplete.kotlinonetimepassword.GoogleAuthenticator
import lynks.common.DatabaseTest
import lynks.util.createDummyUser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TwoFactorServiceTest : DatabaseTest() {

    private val twoFactorService = TwoFactorService()

    @BeforeEach
    fun setup() {
        createDummyUser("user1", "user1@mail.com", "Bob Smith")
    }

    @Test
    fun testValidateTotp() {
        assertThat(twoFactorService.validateTotp("invalid", "code")).isEqualTo(AuthResult.INVALID_CREDENTIALS)
        assertThat(twoFactorService.validateTotp("user1", "code")).isEqualTo(AuthResult.INVALID_CREDENTIALS)
        assertThat(twoFactorService.validateTotp("user1", null)).isEqualTo(AuthResult.SUCCESS)
        twoFactorService.updateTwoFactorEnabled("user1", true)
        val secret = twoFactorService.getTwoFactorSecret("user1") ?: fail("No secret defined")
        val code = GoogleAuthenticator(secret.toByteArray()).generate()
        assertThat(twoFactorService.validateTotp("user1", code)).isEqualTo(AuthResult.SUCCESS)
        assertThat(twoFactorService.validateTotp("user1", "invalid")).isEqualTo(AuthResult.INVALID_CREDENTIALS)
        assertThat(twoFactorService.validateTotp("user1", null)).isEqualTo(AuthResult.TOTP_REQUIRED)
    }

    @Test
    fun testGetTwoFactorSecret() {
        twoFactorService.updateTwoFactorEnabled("user1", true)
        assertThat(twoFactorService.getTwoFactorSecret("user1")).hasSize(16)
        twoFactorService.updateTwoFactorEnabled("user1", false)
        assertThat(twoFactorService.getTwoFactorSecret("user1")).isNull()
    }

    @Test
    fun testGetTwoFactorSecretNotEnabled() {
        assertThat(twoFactorService.getTwoFactorSecret("invalid")).isNull()
        assertThat(twoFactorService.getTwoFactorSecret("user1")).isNull()
    }

    @Test
    fun testUpdateTwoFactorEnabled() {
        assertThat(twoFactorService.updateTwoFactorEnabled("user1", true)).isTrue()
        assertThat(twoFactorService.getTwoFactorSecret("user1")).isNotNull()
        assertThat(twoFactorService.updateTwoFactorEnabled("user1", false)).isTrue()
        assertThat(twoFactorService.getTwoFactorSecret("user1")).isNull()

        assertThat(twoFactorService.updateTwoFactorEnabled("invalid", true)).isFalse()
    }

}
