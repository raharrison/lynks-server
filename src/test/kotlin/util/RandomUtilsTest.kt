package util

import org.junit.Assert.assertEquals
import org.junit.Test

class RandomUtilsTest {

    @Test
    fun testRandomUidLength() {
        val iterations = 1000
        val set = (0 until iterations).map { RandomUtils.generateUid() }.toSet()
        set.forEach( {assertEquals(12, it.length) })
    }

    @Test
    fun testRandomUidCollision() {
        val iterations = 1000
        val set = (0 until iterations).map { RandomUtils.generateUid() }.toSet()
        assertEquals(iterations, set.size)
    }
}