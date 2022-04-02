package lynks.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HashUtilsTest {

    @Test
    fun testSha1Hash() {
        assertThat(HashUtils.sha1Hash("12345")).hasSize(40)
        assertThat(HashUtils.sha1Hash("abcdef")).hasSize(40)
        assertThat(HashUtils.sha1Hash("#343^GvsdfQ£$^vsdofaghp")).hasSize(40)
    }

    @Test
    fun testBcryptHash() {
        assertThat(HashUtils.bcryptHash("12345")).hasSize(60)
        assertThat(HashUtils.bcryptHash("abcdef")).hasSize(60)
        assertThat(HashUtils.bcryptHash("#343^GvsdfQ£$^vsdofaghp")).hasSize(60)
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
