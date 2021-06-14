package util

import com.vladsch.flexmark.util.sequence.BasedSequence
import common.Environment
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import util.markdown.EntryLinkInlineParserExtension
import util.markdown.EntryLinkNode
import util.markdown.MarkdownNodeVisitor
import util.markdown.MarkdownUtils

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
        assertConvertEqual(
            "- [x] finished", """
            <ul>
            <li class="task-list-item"><input type="checkbox" class="task-list-item-checkbox" checked="checked" disabled="disabled" readonly="readonly" />&nbsp;finished</li>
            </ul>

        """.trimIndent()
        )
        assertConvertEqual(
            "- [ ] unfinished", """
            <ul>
            <li class="task-list-item"><input type="checkbox" class="task-list-item-checkbox" disabled="disabled" readonly="readonly" />&nbsp;unfinished</li>
            </ul>

        """.trimIndent()
        )
    }

    @Test
    fun testEntryLinkParserExtensionNotPossible() {
        assertConvertEqual(
            "link is.@123",
            "<p>link is.@123</p>\n"
        )
        assertConvertEqual(
            "link is-@123",
            "<p>link is-@123</p>\n"
        )
    }

    @Test
    fun testVisitAndReplaceNodes() {
        val (replaced, markdown) = MarkdownUtils.visitAndReplaceNodes("first {second} {third}", object : MarkdownNodeVisitor {
            override val pattern = "\\{(.+?)\\}"

            override fun replace(match: MatchResult): String {
                return match.groupValues[1]
            }
        })
        assertThat(replaced).isEqualTo(2)
        assertThat(markdown).isEqualTo("first second third")
    }

    @Test
    fun testVisitAndReplaceNodesNoReplacement() {
        val input = "first {second} {third}"
        val (replaced, markdown) = MarkdownUtils.visitAndReplaceNodes(input, object : MarkdownNodeVisitor {
            override val pattern = "\\{(.+?)\\}"

            override fun replace(match: MatchResult): String? {
                return null
            }
        })
        assertThat(replaced).isZero()
        assertThat(markdown).isEqualTo(input)
    }

    @Test
    fun testVisitAndReplaceNodesNoMatches() {
        val input = "first second third"
        val (replaced, markdown) = MarkdownUtils.visitAndReplaceNodes(input, object : MarkdownNodeVisitor {
            override val pattern = "\\{(.+?)\\}"

            override fun replace(match: MatchResult): String {
                return match.groupValues[1]
            }
        })
        assertThat(replaced).isZero()
        assertThat(markdown).isEqualTo(input)
    }

    @Nested
    inner class Internal {
        @Test
        fun testEntryLinkNode() {
            val sequence = mockk<BasedSequence>(relaxed = true)
            val node = EntryLinkNode(sequence, sequence)
            node.getAstExtra(StringBuilder())
            assertThat(node.segments).isNotNull()
        }

        @Test
        fun testEntryLinkParserExtensionFactory() {
            val factory = EntryLinkInlineParserExtension.Factory()
            assertThat(factory.afterDependents).isNull()
            assertThat(factory.beforeDependents).isNull()
            assertThat(factory.affectsGlobalScope()).isFalse()
            assertThat(factory.characters).isEqualTo("@")
            assertThat(factory.apply(mockk())).isNotNull()
        }
    }

    private fun assertConvertEqual(input: String, output: String) {
        val out = MarkdownUtils.convertToMarkdown(input)
        assertThat(output).isEqualTo(out)
    }

}
