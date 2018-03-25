package util

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.Test
import util.URLUtils.extractQueryParams
import util.URLUtils.extractSource
import java.net.URISyntaxException

class URLUtilsTest {

    @Test
    fun testValidUrlSources() {
        assertThat(extractSource("www.google.com/something")).isEqualTo("google.com")
        assertThat(extractSource("google.com/something")).isEqualTo("google.com")
        assertThat(extractSource("sub.google.com/something")).isEqualTo("sub.google.com")
        assertThat(extractSource("sub.other.google.com/something")).isEqualTo("sub.other.google.com")
        assertThat(extractSource("google.com/something/else")).isEqualTo("google.com")
        assertThat(extractSource("google.com/something/else#werwf?ergerg=erg")).isEqualTo("google.com")
        assertThat(extractSource("http://google.com/something/else")).isEqualTo("google.com")
        assertThat(extractSource("http://www.google.com/something/else")).isEqualTo("google.com")
        assertThat(extractSource("https://google.com/something/else")).isEqualTo("google.com")
        assertThat(extractSource("https://www.google.com/something/else")).isEqualTo("google.com")
        assertThat(extractSource("ftp://www.google.com/something/else")).isEqualTo("ftp")
        assertThat(extractSource("file://somewhere/else")).isEqualTo("file")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidUrlSources() {
        extractSource("http:google.com/something/")
    }

    @Test
    fun testQueryParamExtract() {
        var uri = "http://google.com"
        assertThat(extractQueryParams(uri)).isEmpty()

        uri = "http://youtube.com/?v=abcd123"
        assertThat(extractQueryParams(uri)).hasSize(1).containsExactly(entry("v", "abcd123"))

        uri = "http://youtube.com/?v=abcd123&some=else&empty&third=three"
        assertThat(extractQueryParams(uri)).hasSize(4).containsExactly(
                entry("v", "abcd123"),
                entry("some", "else"),
                entry("empty", null),
                entry("third", "three"))
    }

    @Test(expected = URISyntaxException::class)
    fun testQueryParamInvalidUri() {
        extractQueryParams("invalid query")
    }
}