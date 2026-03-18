package lynks.model

import lynks.group.Tag
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class TagTest {

    @Test
    fun testEqualityById() {
        val tag1 = Tag("same", "name1", null, Instant.EPOCH, Instant.EPOCH)
        val tag2 = Tag("same", "another", "another", Instant.EPOCH, Instant.EPOCH)

        assertThat(tag1).isEqualTo(tag2)
        assertThat(tag1.hashCode()).isEqualTo(tag2.hashCode())

        val tag3 = Tag("id1", "name", null, Instant.EPOCH, Instant.EPOCH)
        val tag4 = Tag("id2", "name", "name", Instant.EPOCH, Instant.EPOCH)

        assertThat(tag3).isNotEqualTo(tag4)
        assertThat(tag3.hashCode()).isNotEqualTo(tag4.hashCode())
    }

}
