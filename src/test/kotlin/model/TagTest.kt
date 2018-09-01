package model

import group.Tag
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TagTest {

    @Test
    fun testEqualityById() {
        val tag1 = Tag("same", "name1", 123456, 4567)
        val tag2 = Tag("same", "another", 345678, 4568)

        assertThat(tag1).isEqualTo(tag2)
        assertThat(tag1.hashCode()).isEqualTo(tag2.hashCode())

        val tag3 = Tag("id1", "name", 123456, 4567)
        val tag4 = Tag("id2", "name", 123456, 4567)

        assertThat(tag3).isNotEqualTo(tag4)
        assertThat(tag3.hashCode()).isNotEqualTo(tag4.hashCode())
    }

}