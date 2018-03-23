package util

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownUtilsTest {

    @Test
    fun testBasicConvert() {
        assertConvertEqual("# header\n\nsome text", "<h1>header</h1>\n<p>some text</p>\n")
    }

    @Test
    fun testUrlConvert() {
        assertConvertEqual("http://google.com", "<p><a href=\"http://google.com\">http://google.com</a></p>\n")
    }

    private fun assertConvertEqual(input: String, output: String) {
        val out = MarkdownUtils.convertToMarkdown(input)
        assertEquals(output, out)
    }

}