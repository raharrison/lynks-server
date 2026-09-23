package lynks.user

import dev.turingcomplete.kotlinonetimepassword.GoogleAuthenticator
import lynks.common.DatabaseTest
import lynks.common.UserId
import lynks.util.activateUser
import lynks.util.createDummyUser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class TwoFactorServiceTest : DatabaseTest() {

    private val twoFactorService = TwoFactorService()
    private val invalidUser = UserId("invalid")
    private val user1 = UserId("user1-id")

    @BeforeEach
    fun setup() {
        createDummyUser("user1", "Bob Smith", id = user1)
    }

    @Test
    fun testValidateTotp() {
        assertThat(twoFactorService.validateTotp(invalidUser, "code")).isEqualTo(AuthResult.INVALID_CREDENTIALS)
        assertThat(twoFactorService.validateTotp(user1, "code")).isEqualTo(AuthResult.INVALID_CREDENTIALS)
        assertThat(twoFactorService.validateTotp(user1, null)).isEqualTo(AuthResult.SUCCESS)
        twoFactorService.updateTwoFactorEnabled(user1, true)
        val secret = twoFactorService.getTwoFactorSecret(user1) ?: fail("No secret defined")
        val code = GoogleAuthenticator(secret.toByteArray()).generate()
        assertThat(twoFactorService.validateTotp(user1, code)).isEqualTo(AuthResult.SUCCESS)
        assertThat(twoFactorService.validateTotp(user1, "invalid")).isEqualTo(AuthResult.INVALID_CREDENTIALS)
        assertThat(twoFactorService.validateTotp(user1, null)).isEqualTo(AuthResult.TOTP_REQUIRED)
    }

    @Test
    fun testGetTwoFactorSecret() {
        twoFactorService.updateTwoFactorEnabled(user1, true)
        assertThat(twoFactorService.getTwoFactorSecret(user1)).hasSize(16)
        twoFactorService.updateTwoFactorEnabled(user1, false)
        assertThat(twoFactorService.getTwoFactorSecret(user1)).isNull()
    }

    @Test
    fun testGetTwoFactorSecretNotEnabled() {
        assertThat(twoFactorService.getTwoFactorSecret(invalidUser)).isNull()
        assertThat(twoFactorService.getTwoFactorSecret(user1)).isNull()
    }

    @Test
    fun testValidateTotpAcceptsAdjacentWindows() {
        twoFactorService.updateTwoFactorEnabled(user1, true)
        val secret = twoFactorService.getTwoFactorSecret(user1) ?: fail("No secret defined")
        val gen = GoogleAuthenticator(secret.toByteArray())
        val window = 30_000L
        val now = System.currentTimeMillis()
        val prevCode = gen.generate(Date(now - window))
        val nextCode = gen.generate(Date(now + window))
        assertThat(twoFactorService.validateTotp(user1, prevCode)).isEqualTo(AuthResult.SUCCESS)
        assertThat(twoFactorService.validateTotp(user1, nextCode)).isEqualTo(AuthResult.SUCCESS)
    }

    @Test
    fun testUpdateTwoFactorEnabled() {
        assertThat(twoFactorService.updateTwoFactorEnabled(user1, true)).isTrue()
        assertThat(twoFactorService.getTwoFactorSecret(user1)).isNotNull()
        assertThat(twoFactorService.updateTwoFactorEnabled(user1, false)).isTrue()
        assertThat(twoFactorService.getTwoFactorSecret(user1)).isNull()

        assertThat(twoFactorService.updateTwoFactorEnabled(invalidUser, true)).isFalse()
    }

    @Test
    fun testSecretsAreSeparatePerUser() {
        val user2 = createDummyUser("user2")
        twoFactorService.updateTwoFactorEnabled(user1, true)
        assertThat(twoFactorService.getTwoFactorSecret(user1)).isNotNull()
        assertThat(twoFactorService.getTwoFactorSecret(user2)).isNull()
        assertThat(twoFactorService.validateTotp(user2, null)).isEqualTo(AuthResult.SUCCESS)
    }

    @Test
    fun testDeactivatedUserHasNoSecret() {
        twoFactorService.updateTwoFactorEnabled(user1, true)
        activateUser("user1", false)
        assertThat(twoFactorService.getTwoFactorSecret(user1)).isNull()
        assertThat(twoFactorService.updateTwoFactorEnabled(user1, false)).isFalse()
    }

}
