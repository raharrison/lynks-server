package model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tag.Tag

class TagTest {

    @Test
    fun testEqualityById() {
        val tag1 = Tag("same", "name1", mutableSetOf(), 123456)
        val tag2 = Tag("same", "another", mutableSetOf(tag1), 345678)

        assertThat(tag1).isEqualTo(tag2)
        assertThat(tag1.hashCode()).isEqualTo(tag2.hashCode())

        val tag3 = Tag("id1", "name", mutableSetOf(tag1, tag2), 123456)
        val tag4 = Tag("id2", "name", mutableSetOf(tag1, tag2), 123456)

        assertThat(tag3).isNotEqualTo(tag4)
        assertThat(tag3.hashCode()).isNotEqualTo(tag4.hashCode())
    }

}