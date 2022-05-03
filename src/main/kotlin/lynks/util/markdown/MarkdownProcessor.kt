package lynks.util.markdown

import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughSubscriptExtension
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.formatter.Formatter
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.ast.NodeVisitor
import com.vladsch.flexmark.util.data.MutableDataSet
import lynks.entry.EntryService
import lynks.resource.ResourceManager
import lynks.resource.TempImageMarkdownVisitor

class MarkdownProcessor(private val resourceManager: ResourceManager, private val entryService: EntryService) {

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

    fun convertToMarkdown(text: String): String {
        return renderer.render(parser.parse(text))
    }

    fun convertAndProcess(text: String, entryId: String): Triple<Int, String, String> {
        val doc = parser.parse(text)
        val visitor = TempImageMarkdownVisitor(entryId, resourceManager)
        visitor.replaceUrl(doc)
        val markdown = formatter.render(doc)
        return Triple(visitor.visitedCount, markdown, renderer.render(doc))
    }

    fun visit(text: String, visitor: NodeVisitor) {
        val doc = parser.parse(text)
        visitor.visit(doc)
    }

}
