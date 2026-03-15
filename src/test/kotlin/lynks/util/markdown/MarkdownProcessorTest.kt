package lynks.util.markdown

import com.vladsch.flexmark.ast.Link
import com.vladsch.flexmark.util.ast.NodeVisitor
import com.vladsch.flexmark.util.ast.VisitHandler
import com.vladsch.flexmark.util.sequence.BasedSequence
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import lynks.common.*
import lynks.entry.EntryService
import lynks.resource.Resource
import lynks.resource.ResourceManager
import lynks.resource.ResourceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Path

class MarkdownProcessorTest {

    private val resourceManager = mockk<ResourceManager>()
    private val entryService = mockk<EntryService>()
    private val markdownProcessor = MarkdownProcessor(resourceManager, entryService)

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
        every { entryService.get(EntryId("1234")) } returns Note(EntryId("1234"), "My Note Title", "content", "content", 123, 123)

        assertConvertEqual(
            "link is @1234",
            "<p>link is <a href=\"/notes/1234\"><strong>My Note Title</strong></a></p>\n"
        )
        assertConvertEqual(
            "link is @1234 and more",
            "<p>link is <a href=\"/notes/1234\"><strong>My Note Title</strong></a> and more</p>\n"
        )

        verify(exactly = 2) { entryService.get(EntryId("1234")) }
    }

    @Test
    fun testEntryLinkRendersLinkTitle() {
        every { entryService.get(EntryId("linkid")) } returns
            lynks.common.Link(EntryId("linkid"), "My Link Title", "http://example.com", "example.com", null, 123, 123)

        assertConvertEqual(
            "see @linkid here",
            "<p>see <a href=\"/links/linkid\"><strong>My Link Title</strong></a> here</p>\n"
        )
    }

    @Test
    fun testEntryLinkRendersFileTitle() {
        every { entryService.get(EntryId("fileid")) } returns
            lynks.common.File(EntryId("fileid"), "My File Title", 123, 123)

        assertConvertEqual(
            "see @fileid here",
            "<p>see <a href=\"/files/fileid\"><strong>My File Title</strong></a> here</p>\n"
        )
    }

    @Test
    fun testEntryLinkSnippetFallsBackToId() {
        every { entryService.get(EntryId("snipid")) } returns
            Snippet(EntryId("snipid"), "some code", "<p>code</p>", 123, 123)

        assertConvertEqual(
            "see @snipid here",
            "<p>see <a href=\"/snippets/snipid\"><strong>@snipid</strong></a> here</p>\n"
        )
    }

    @Test
    fun testEntryLinkEntryNotFound() {
        every { entryService.get(EntryId("1234")) } returns null
        assertConvertEqual("something @1234 else", "<p>something @1234 else</p>\n")
        verify(exactly = 1) { entryService.get(EntryId("1234")) }
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
    fun testMarkdownVisitor() {
        val markdown = "first [Link1](href1) and another [Link2](href2) the end"
        var visits = 0
        markdownProcessor.visit(markdown, NodeVisitor(VisitHandler(Link::class.java) {
            visits++
        }))
        assertThat(visits).isEqualTo(2)
    }

    @Nested
    inner class TempImageReplace {

        private val eid = EntryId("eid")
        private val imageInput = "${TEMP_URL}abc/one.png"
        private val fullInput = "![desc]($imageInput)"

        @Test
        fun testNoGroupFound() {
            val raw = "some text"
            val (replaced, markdown, html) = markdownProcessor.convertAndProcess(raw, eid)
            assertThat(replaced).isZero()
            assertThat(markdown.trim()).isEqualTo(raw)
            assertThat(html).isEqualTo("<p>some text</p>\n")
            verify(exactly = 0) { resourceManager.migrateGeneratedResources(eid, any()) }
        }

        @Test
        fun testGroupsReplaced() {
            every { resourceManager.constructTempBasePath(IMAGE_UPLOAD_BASE) } returns Path.of("migrated/")
            val resources = listOf(Resource(ResourceId("rid"), "pid", EntryId("eid"), 1, "one", "png", ResourceType.UPLOAD, 12, 123L))
            every { resourceManager.migrateGeneratedResources(eid, any()) } returns resources
            val (replaced, markdown, html) = markdownProcessor.convertAndProcess(fullInput, eid)
            assertThat(replaced).isOne()
            assertThat(markdown).isEqualTo("![desc](${Environment.server.rootPath}/entry/$eid/resource/rid)\n")
            assertThat(html).isEqualTo("<p><img src=\"/api/entry/eid/resource/rid\" alt=\"desc\" /></p>\n")
            verify(exactly = 1) { resourceManager.migrateGeneratedResources(eid, any()) }
        }

        @Test
        fun testNoResourcesMigrated() {
            every { resourceManager.constructTempBasePath(IMAGE_UPLOAD_BASE) } returns Path.of("migrated/")
            every { resourceManager.migrateGeneratedResources(eid, any()) } returns emptyList()
            val (replaced, markdown) = markdownProcessor.convertAndProcess(fullInput, eid)
            assertThat(replaced).isZero()
            assertThat(markdown.trim()).isEqualTo(fullInput)
            verify(exactly = 1) { resourceManager.migrateGeneratedResources(eid, any()) }
        }

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
        val out = markdownProcessor.convertToMarkdown(input)
        assertThat(output).isEqualTo(out)
    }

}
