package lynks.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors

class HashUtilsTest {

    @Test
    fun testSha1Hash() {
        assertThat(HashUtils.sha1Hash("12345")).hasSize(40)
        assertThat(HashUtils.sha1Hash("abcdef")).hasSize(40)
        assertThat(HashUtils.sha1Hash("#343^GvsdfQ£$^vsdofaghp")).hasSize(40)
    }

    @Test
    fun testSha1HashConcurrency() {
        val expected = HashUtils.sha1Hash("concurrent-input")
        val pool = Executors.newFixedThreadPool(8)
        val futures = (1..50).map { pool.submit<String> { HashUtils.sha1Hash("concurrent-input") } }
        pool.shutdown()
        assertThat(futures.map { it.get() }).allMatch { it == expected }
    }

    @Test
    fun testBcryptHash() {
        assertThat(HashUtils.bcryptHash("12345")).hasSize(60)
        assertThat(HashUtils.bcryptHash("abcdef")).hasSize(60)
        assertThat(HashUtils.bcryptHash("#343^GvsdfQ£$^vsdofaghp")).hasSize(60)
    }

    @Test
    fun testBcryptHashMatchesDetectionPattern() {
        val bcryptPattern = Regex("""^\$2[abxy]\$\d{2}\$""")
        assertThat(HashUtils.bcryptHash("password")).matches { bcryptPattern.containsMatchIn(it) }
        assertThat(HashUtils.bcryptHash("another")).matches { bcryptPattern.containsMatchIn(it) }
        // plain strings should not match
        assertThat(bcryptPattern.containsMatchIn("plaintext")).isFalse()
        assertThat(bcryptPattern.containsMatchIn("")).isFalse()
    }

    @Test
    fun testVerifyBcryptHash() {
        val raw = "abcdef12345"
        val hash = HashUtils.bcryptHash(raw).toCharArray()
        assertThat(HashUtils.verifyBcryptHash(raw.toCharArray(), hash)).isTrue()
        assertThat(HashUtils.verifyBcryptHash(raw.toCharArray(), "modified".toCharArray())).isFalse()
        assertThat(HashUtils.verifyBcryptHash("abcdef".toCharArray(), hash)).isFalse()
    }

}
