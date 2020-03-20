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
    fun testAutoLinking() {
        assertConvertEqual("http://google.com", "<p><a href=\"http://google.com\">http://google.com</a></p>\n")
    }

    @Test
    fun testEntryLinks() {
        val iterations = 1000
        val prefix = Environment.server.rootPath
        repeat(iterations) {
            val id = RandomUtils.generateUid()
            assertConvertEqual(
                "link is @$id",
                "<p>link is <a href=\"$prefix/entry/$id\"><strong>@$id</strong></a></p>\n"
            )
            assertConvertEqual(
                "link is @$id and more",
                "<p>link is <a href=\"$prefix/entry/$id\"><strong>@$id</strong></a> and more</p>\n"
            )
        }
    }

    @Test
    fun testStrikethroughSubscript() {
        assertConvertEqual("~~striked~~", "<p><del>striked</del></p>\n")
        assertConvertEqual("~subscript~", "<p><sub>subscript</sub></p>\n")
    }

    @Test
    fun testTaskLists() {
        assertConvertEqual("- [x] finished", """
            <ul>
            <li class="task-list-item"><input type="checkbox" class="task-list-item-checkbox" checked="checked" disabled="disabled" readonly="readonly" />&nbsp;finished</li>
            </ul>
            
        """.trimIndent())
        assertConvertEqual("- [ ] unfinished", """
            <ul>
            <li class="task-list-item"><input type="checkbox" class="task-list-item-checkbox" disabled="disabled" readonly="readonly" />&nbsp;unfinished</li>
            </ul>
        
        """.trimIndent())
    }

    private fun assertConvertEqual(input: String, output: String) {
        val out = MarkdownUtils.convertToMarkdown(input)
        assertThat(output).isEqualTo(out)
    }

}