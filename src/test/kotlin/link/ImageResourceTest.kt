package link

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ImageResourceTest {

    @Test
    fun testEquality() {
        val i1 = ImageResource(byteArrayOf(1, 2), "txt")
        val i2 = ImageResource(byteArrayOf(1, 2), "txt")
        val i3 = ImageResource(byteArrayOf(1, 2), "pdf")
        val i4 = ImageResource(byteArrayOf(1, 2, 3), "txt")
        val i5: ImageResource? = null

        assertThat(i1 == i2).isTrue()
        assertThat(i1 == i3).isFalse()
        assertThat(i1 == i4).isFalse()
        assertThat(i3 == i4).isFalse()
        assertThat(i1 == i5).isFalse()
    }
}