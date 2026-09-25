package lynks.auth

import lynks.common.Environment
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class AuthConfigTest {

    private val oidc = Environment.Oidc(
        enabled = true,
        issuer = "https://auth.example.com",
        clientId = "lynks",
        clientSecret = "secret",
        redirectUri = "https://lynks.example.com/api/auth/oidc/callback",
        label = "Sign in with SSO"
    )
    private val valid = Environment.auth.copy(enabled = true, passwordLoginEnabled = true, oidc = oidc)

    @Test
    fun testDefaults() {
        assertThat(Environment.auth.passwordLoginEnabled).isTrue()
        assertThat(Environment.auth.session.idleDays).isEqualTo(30)
        assertThat(Environment.auth.session.maxDays).isEqualTo(90)
        assertThat(Environment.auth.oidc.enabled).isFalse()
        assertThat(Environment.auth.oidc.label).isEqualTo("Sign in with SSO")
        Environment.auth.validate()
    }

    @Test
    fun testValidOidcConfig() {
        valid.validate()
        valid.copy(passwordLoginEnabled = false).validate()
    }

    @Test
    fun testNobodyCouldSignIn() {
        assertThatThrownBy { valid.copy(passwordLoginEnabled = false, oidc = oidc.copy(enabled = false)).validate() }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun testOidcNeedsAuthEnabled() {
        assertThatThrownBy { valid.copy(enabled = false).validate() }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun testOidcNeedsEveryProviderSetting() {
        listOf(
            oidc.copy(issuer = null),
            oidc.copy(issuer = "auth.example.com"),
            oidc.copy(issuer = "ftp://auth.example.com"),
            oidc.copy(clientId = " "),
            oidc.copy(clientSecret = null),
            oidc.copy(redirectUri = ""),
            oidc.copy(redirectUri = "/api/auth/oidc/callback"),
            oidc.copy(redirectUri = "not a url")
        ).forEach {
            assertThatThrownBy { valid.copy(oidc = it).validate() }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun testSessionLifetimes() {
        assertThatThrownBy { valid.copy(session = Environment.AuthSession(0, 90)).validate() }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { valid.copy(session = Environment.AuthSession(30, 7)).validate() }
            .isInstanceOf(IllegalArgumentException::class.java)
        valid.copy(session = Environment.AuthSession(7, 7)).validate()
    }
}
