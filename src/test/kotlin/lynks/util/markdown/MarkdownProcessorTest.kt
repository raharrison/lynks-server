package lynks.util.markdown

import com.vladsch.flexmark.ast.Link
import com.vladsch.flexmark.util.ast.NodeVisitor
import com.vladsch.flexmark.util.ast.VisitHandler
import com.vladsch.flexmark.util.sequence.BasedSequence
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import lynks.common.*
import lynks.entry.EntryService
import lynks.resource.PendingResource
import lynks.resource.ResourceManager
import lynks.resource.ResourceType
import lynks.util.TEST_USER
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Instant

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
        every { entryService.get(TEST_USER, EntryId("1234")) } returns Note(
            EntryId("1234"),
            "My Note Title",
            "content",
            "content",
            Instant.EPOCH,
            Instant.EPOCH
        )

        assertConvertEqual(
            "link is @1234",
            "<p>link is <a href=\"/notes/1234\"><strong>My Note Title</strong></a></p>\n"
        )
        assertConvertEqual(
            "link is @1234 and more",
            "<p>link is <a href=\"/notes/1234\"><strong>My Note Title</strong></a> and more</p>\n"
        )

        verify(exactly = 2) { entryService.get(TEST_USER, EntryId("1234")) }
    }

    @Test
    fun testEntryLinkRendersLinkTitle() {
        every { entryService.get(TEST_USER, EntryId("linkid")) } returns
            lynks.common.Link(EntryId("linkid"), "My Link Title", "http://example.com", "example.com", null, Instant.EPOCH, Instant.EPOCH)

        assertConvertEqual(
            "see @linkid here",
            "<p>see <a href=\"/links/linkid\"><strong>My Link Title</strong></a> here</p>\n"
        )
    }

    @Test
    fun testEntryLinkRendersFileTitle() {
        every { entryService.get(TEST_USER, EntryId("fileid")) } returns
            lynks.common.File(EntryId("fileid"), "My File Title", Instant.EPOCH, Instant.EPOCH)

        assertConvertEqual(
            "see @fileid here",
            "<p>see <a href=\"/files/fileid\"><strong>My File Title</strong></a> here</p>\n"
        )
    }

    @Test
    fun testEntryLinkSnippetFallsBackToId() {
        every { entryService.get(TEST_USER, EntryId("snipid")) } returns
            Snippet(EntryId("snipid"), "some code", "<p>code</p>", Instant.EPOCH, Instant.EPOCH)

        assertConvertEqual(
            "see @snipid here",
            "<p>see <a href=\"/snippets/snipid\"><strong>@snipid</strong></a> here</p>\n"
        )
    }

    @Test
    fun testEntryLinkEntryNotFound() {
        every { entryService.get(TEST_USER, EntryId("1234")) } returns null
        assertConvertEqual("something @1234 else", "<p>something @1234 else</p>\n")
        verify(exactly = 1) { entryService.get(TEST_USER, EntryId("1234")) }
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

        @Test
        fun testNoImagesAttachesNothing() {
            val raw = "some text"
            val (markdown, html) = markdownProcessor.convertAndProcess(TEST_USER, raw, eid)
            assertThat(markdown.trim()).isEqualTo(raw)
            assertThat(html).isEqualTo("<p>some text</p>\n")
            verify(exactly = 0) { resourceManager.attach(eid, any()) }
        }

        @Test
        fun testTempImageIsAttachedAndRewritten() {
            val file = Path.of("uploads", "one.png")
            every { resourceManager.findTempUpload(TEST_USER, "one.png") } returns file
            val attached = slot<List<PendingResource>>()
            every { resourceManager.attach(eid, capture(attached)) } returns emptyList()

            val (markdown, html) = markdownProcessor.convertAndProcess(TEST_USER, "![desc](${TEMP_UPLOAD_URL}one.png)", eid)

            val reserved = attached.captured.single()
            assertThat(reserved.resourceType).isEqualTo(ResourceType.UPLOAD)
            assertThat(reserved.tempPath).isEqualTo(file)
            assertThat(markdown)
                .isEqualTo("![desc](${Environment.server.rootPath}/entry/$eid/resource/${reserved.id})")
            assertThat(html)
                .isEqualTo("<p><img src=\"/api/entry/eid/resource/${reserved.id}\" alt=\"desc\" /></p>\n")
        }

        @Test
        fun testMissingTempImageIsLeftAlone() {
            val input = "![desc](${TEMP_UPLOAD_URL}one.png)"
            every { resourceManager.findTempUpload(TEST_USER, "one.png") } returns null

            val (markdown, _) = markdownProcessor.convertAndProcess(TEST_USER, input, eid)

            assertThat(markdown.trim()).isEqualTo(input)
            verify(exactly = 0) { resourceManager.attach(eid, any()) }
        }

        @Test
        fun testOtherTempImageIsIgnored() {
            val input = "![desc](${TEMP_URL}abc/thumbnail.jpg)"

            val (markdown, _) = markdownProcessor.convertAndProcess(TEST_USER, input, eid)

            assertThat(markdown.trim()).isEqualTo(input)
            verify(exactly = 0) { resourceManager.findTempUpload(TEST_USER, any()) }
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
        val out = markdownProcessor.convertToMarkdown(TEST_USER, input)
        assertThat(output).isEqualTo(out)
    }

}
