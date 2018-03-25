package util

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import util.URLUtils.extractSource

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
}