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

}
