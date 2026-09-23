package lynks.util.markdown

import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughSubscriptExtension
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.formatter.Formatter
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.ast.Document
import com.vladsch.flexmark.util.ast.NodeVisitor
import com.vladsch.flexmark.util.data.DataKey
import com.vladsch.flexmark.util.data.MutableDataSet
import lynks.common.EntryId
import lynks.common.UserId
import lynks.entry.EntryService
import lynks.resource.ResourceManager
import lynks.resource.TempImageMarkdownVisitor

data class ProcessedMarkdown(val markdown: String, val html: String)

// Mentions are resolved as the document's owner, so another user's entry renders as plain text
internal val DOCUMENT_OWNER = DataKey("LYNKS_DOCUMENT_OWNER", "")

class MarkdownProcessor(private val resourceManager: ResourceManager, entryService: EntryService) {

    private val parser: Parser
    private val formatter: Formatter
    private val renderer: HtmlRenderer

    init {
        val options = MutableDataSet()
            .set(HtmlRenderer.SOFT_BREAK, "<br />\n")
            .set(TablesExtension.COLUMN_SPANS, false)
            .set(TablesExtension.APPEND_MISSING_COLUMNS, true)
            .set(TablesExtension.DISCARD_EXTRA_COLUMNS, true)
            .set(TablesExtension.HEADER_SEPARATOR_COLUMN_MATCH, true)
            .set(
                Parser.EXTENSIONS, listOf(
                    TablesExtension.create(),
                    StrikethroughSubscriptExtension.create(),
                    AutolinkExtension.create(),
                    TaskListExtension.create(),
                    EntryLinkExtension(entryService)
                )
            ).toImmutable()
        parser = Parser.builder(options).build()
        formatter = Formatter.builder(options).build()
        renderer = HtmlRenderer.builder(options).build()
    }

    private fun parse(userId: UserId, text: String): Document =
        parser.parse(text).also { it.set(DOCUMENT_OWNER, userId.value) }

    fun convertToMarkdown(userId: UserId, text: String): String {
        return renderer.render(parse(userId, text))
    }

    // Pasted temp images are attached to the entry and their links rewritten to the resource
    fun convertAndProcess(userId: UserId, text: String, entryId: EntryId): ProcessedMarkdown {
        val doc = parse(userId, text)
        val visitor = TempImageMarkdownVisitor(userId, entryId, resourceManager)
        visitor.replaceUrl(doc)
        if (visitor.pending.isNotEmpty()) {
            resourceManager.attach(entryId, visitor.pending)
        }
        return ProcessedMarkdown(formatter.render(doc).trim(), renderer.render(doc))
    }

    fun visit(text: String, visitor: NodeVisitor) {
        val doc = parser.parse(text)
        visitor.visit(doc)
    }

}
