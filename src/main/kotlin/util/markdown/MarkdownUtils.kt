package util.markdown

import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughSubscriptExtension
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet

object MarkdownUtils {

    fun convertToMarkdown(text: String): String {
        return renderer.render(parser.parse(text))
    }

    private val parser: Parser
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
                    EntryLinkExtension()
                )
            ).toImmutable()
        parser = Parser.builder(options).build()
        renderer = HtmlRenderer.builder(options).build()
    }
}