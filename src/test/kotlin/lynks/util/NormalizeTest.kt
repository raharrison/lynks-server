package lynks.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NormalizeTest {

    @Test
    fun testNormalizeStringWithSpaces() {
        assertThat(Normalize.normalize("")).isEqualTo("")
        assertThat(Normalize.normalize("something & and else - other")).isEmpty()
        assertThat(Normalize.normalize("\n\n   \n  tortoise \n  \r  ")).isEqualTo("tortoise")
    }

    @Test
    fun testNormalizeStringWithEntities() {
        assertThat(Normalize.normalize("  &nbsp;  &amp; &amp; &mdash; &gt;")).isEmpty()
    }

    @Test
    fun testRemoveStopwords() {
        val original = "The quick brown fox jumps over the lazy dog"
        val target = "quick brown fox jumps lazy dog"
        assertThat(Normalize.normalize(original)).isEqualTo(target)
    }

    @Test
    fun testMostCommonWords() {
        assertThat(Normalize.mostCommonWords("The quick brown fox jumps over the lazy dog", 2)).isEqualTo("The quick")
        assertThat(Normalize.mostCommonWords("The quick brown fox jumps over the lazy dog", 15)).isEqualTo("The quick brown fox jumps over the lazy dog")
        assertThat(Normalize.mostCommonWords("one two three four two three three", 2)).isEqualTo("two three two three three")
        assertThat(Normalize.mostCommonWords("one two three three three two", 1)).isEqualTo("three three three")
    }

    @Test
    fun testConvertToDbColumnName() {
        assertThat(Normalize.convertToDbColumnName(null)).isNull()
        assertThat(Normalize.convertToDbColumnName("date")).isEqualTo("date")
        assertThat(Normalize.convertToDbColumnName("dateCreated")).isEqualTo("date_Created")
        assertThat(Normalize.convertToDbColumnName("firstAndSecondAndThird")).isEqualTo("first_And_Second_And_Third")
    }

}
