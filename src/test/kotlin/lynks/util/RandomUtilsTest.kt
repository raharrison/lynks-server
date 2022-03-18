package lynks.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RandomUtilsTest {

    @Test
    fun testRandomUidLength() {
        val iterations = 1000
        val set = (0 until iterations).map { RandomUtils.generateUid() }.toSet()
        set.forEach { assertThat(14).isEqualTo(it.length) }
    }

    @Test
    fun testRandomUidCollision() {
        val iterations = 1000
        val set = (0 until iterations).map { RandomUtils.generateUid() }.toSet()
        assertThat(iterations).isEqualTo(set.size)
    }
}
