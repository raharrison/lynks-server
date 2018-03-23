package util

import org.junit.Assert.assertEquals
import org.junit.Test
import util.URLUtils.extractSource

class URLUtilsTest {

    @Test
    fun testValidUrlSources() {
        assertEquals(extractSource("www.google.com/something"), "google.com")
        assertEquals(extractSource("google.com/something"), "google.com")
        assertEquals(extractSource("sub.google.com/something"), "sub.google.com")
        assertEquals(extractSource("sub.other.google.com/something"), "sub.other.google.com")
        assertEquals(extractSource("google.com/something/else"), "google.com")
        assertEquals(extractSource("google.com/something/else#werwf?ergerg=erg"), "google.com")
        assertEquals(extractSource("http://google.com/something/else"), "google.com")
        assertEquals(extractSource("http://www.google.com/something/else"), "google.com")
        assertEquals(extractSource("https://google.com/something/else"), "google.com")
        assertEquals(extractSource("https://www.google.com/something/else"), "google.com")
        assertEquals(extractSource("ftp://www.google.com/something/else"), "ftp")
        assertEquals(extractSource("file://somewhere/else"), "file")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidUrlSources() {
        extractSource("http:google.com/something/")
    }
}