package util

import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.superscript.SuperscriptExtension
import com.vladsch.flexmark.util.data.MutableDataSet

object MarkdownUtils {

    fun convertToMarkdown(text: String): String {
        return renderer.render(parser.parse(text))
    }

    private var parser: Parser
    private var renderer: HtmlRenderer

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
                    StrikethroughExtension.create(),
                    SuperscriptExtension.create(),
                    AutolinkExtension.create()
                )
            ).toImmutable()
        parser = Parser.builder(options).build()
        renderer = HtmlRenderer.builder(options).build()
    }

}
