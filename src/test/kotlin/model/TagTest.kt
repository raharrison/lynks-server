package model

import org.junit.Assert.*
import org.junit.Test

class TagTest {

    @Test
    fun testEqualityById() {
        val tag1 = Tag("same", "name1", mutableSetOf(), 123456)
        val tag2 = Tag("same", "another", mutableSetOf(tag1), 345678)

        assertTrue(tag1 == tag2)
        assertEquals(tag1.hashCode(), tag2.hashCode())

        val tag3 = Tag("id1", "name", mutableSetOf(tag1, tag2), 123456)
        val tag4 = Tag("id2", "name", mutableSetOf(tag1, tag2), 123456)

        assertFalse(tag3 == tag4)
        assertNotEquals(tag3.hashCode(), tag4.hashCode())
    }

}