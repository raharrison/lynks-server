package lynks.auth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReturnToTest {

    @Test
    fun testSameOriginPathsAreKept() {
        listOf(
            "/", "/entries/abc", "/entries?tag=a&page=2", "/notes#section", "/apis", "/settings?tab=security",
            "/%2F%2Fevil.example", "/entries/%5Cx", "/tags/caf%C3%A9", "/entries/a.b", "/entries/..x"
        ).forEach {
            assertThat(ReturnTo.sanitize(it, "/api")).describedAs(it).isEqualTo(it)
        }
    }

    @Test
    fun testAnythingElseBecomesRoot() {
        listOf(
            null, "", "entries", "https://evil.example", "//evil.example", "//evil.example/path", "/\\evil.example",
            "/entries\\..\\x", "/entries\nSet-Cookie: x", "/entries\r\n", "/entries\u0000", "/api", "/api/user",
            "/api?x=1", "/api#x", "/login", "/login?returnTo=/x", "javascript:alert(1)", "/" + "a".repeat(3000),
            "/./api/user", "/%2e/api/user", "/entries/../api/user", "/entries/%2E%2E/x", "/%61pi/user", "/./login",
            "/%6Cogin", "/entries/%zz"
        ).forEach {
            assertThat(ReturnTo.sanitize(it, "/api")).describedAs(it).isEqualTo("/")
        }
    }
}
