package util

import common.Environment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MarkdownUtilsTest {

    @Test
    fun testBasicConvert() {
        assertConvertEqual("# header\n\nsome text", "<h1>header</h1>\n<p>some text</p>\n")
    }

    @Test
    fun testUrlConvert() {
        assertConvertEqual("http://google.com", "<p><a href=\"http://google.com\">http://google.com</a></p>\n")
    }

    @Test
    fun testEntryLinks() {
        val id = RandomUtils.generateUid()
        val prefix = Environment.server.rootPath
        assertConvertEqual("link is @$id", "<p>link is <a href=\"$prefix/entry/$id\"><strong>@$id</strong></a></p>\n")
    }

    private fun assertConvertEqual(input: String, output: String) {
        val out = MarkdownUtils.convertToMarkdown(input)
        assertThat(output).isEqualTo(out)
    }

}