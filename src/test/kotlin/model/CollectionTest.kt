package model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tag.Collection

class CollectionTest {

    @Test
    fun testEqualityById() {
        val col1 = Collection("same", "name1", mutableSetOf(), 123456, 6789)
        val col2 = Collection("same", "another", mutableSetOf(col1), 345678, 34756)

        assertThat(col1).isEqualTo(col2)
        assertThat(col1.hashCode()).isEqualTo(col2.hashCode())

        val col3 = Collection("id1", "name", mutableSetOf(col1, col2), 123456, 4567)
        val col4 = Collection("id2", "name", mutableSetOf(col1, col2), 123456, 4567)

        assertThat(col3).isNotEqualTo(col4)
        assertThat(col3.hashCode()).isNotEqualTo(col4.hashCode())
    }

}