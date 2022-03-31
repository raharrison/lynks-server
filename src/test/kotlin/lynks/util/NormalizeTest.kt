package lynks.util

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

    @Test
    fun testRemoveStopwords() {
        val original = "The quick brown fox jumps over the lazy dog"
        val target = "quick brown fox jumps lazy dog"
        assertThat(Normalize.removeStopwords(original)).isEqualTo(target)
    }

    @Test
    fun testConvertToDbColumnName() {
        assertThat(Normalize.convertToDbColumnName(null)).isNull()
        assertThat(Normalize.convertToDbColumnName("date")).isEqualTo("date")
        assertThat(Normalize.convertToDbColumnName("dateCreated")).isEqualTo("date_Created")
        assertThat(Normalize.convertToDbColumnName("firstAndSecondAndThird")).isEqualTo("first_And_Second_And_Third")
    }

}
