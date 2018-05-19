package util

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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

    @Test
    fun testInvalidUrlSources() {
        assertThrows<IllegalArgumentException> { extractSource("http:google.com/something/") }
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

    @Test
    fun testQueryParamInvalidUri() {
        assertThrows<URISyntaxException> { extractQueryParams("invalid query") }
    }

    @Test
    fun testValidUrl() {
        assertThat(URLUtils.isValidUrl("google.com")).isTrue()
        assertThat(URLUtils.isValidUrl("google.com/something")).isTrue()
        assertThat(URLUtils.isValidUrl("http://google.com")).isTrue()
        assertThat(URLUtils.isValidUrl("https://google.com")).isTrue()
        assertThat(URLUtils.isValidUrl("www.google.com")).isTrue()
        assertThat(URLUtils.isValidUrl("google.com?v=abc")).isTrue()
    }

    @Test
    fun testInvalidUrl() {
        assertThat(URLUtils.isValidUrl("something")).isFalse()
        assertThat(URLUtils.isValidUrl("http:something")).isFalse()
    }

    @Test
    fun testUrlDecode() {
        val uri = "http://youtube.com/?v=something%5Eelse*if%2Bsome()!%20-%20more"
        assertThat(extractQueryParams(uri)).hasSize(1).containsExactly(entry("v", "something^else*if some()! - more"))
    }
}