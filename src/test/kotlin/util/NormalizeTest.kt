package util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NormalizeTest {

    @Test
    fun testNormalizeStringWithSpaces() {
        assertThat(Normalize.normalize("")).isEqualTo("")
        assertThat(Normalize.normalize("\n\n   \n  something \n  \r  ")).isEqualTo("something")
    }

    @Test
    fun testNormalizeStringWithEntities() {
        assertThat(Normalize.normalize("  &nbsp;  &amp; &amp; &mdash;")).isEqualTo("& & -")
    }

}
