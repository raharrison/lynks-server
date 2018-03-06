package util

import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.superscript.SuperscriptExtension
import com.vladsch.flexmark.util.options.MutableDataSet
import java.util.*

object MarkdownUtils {

    fun convertToMarkdown(text: String): String {
        return renderer.render(parser.parse(text))
    }

    private var parser: Parser
    private var renderer: HtmlRenderer

    init {
        val options = MutableDataSet()
        options.set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create(),
                StrikethroughExtension.create(),
                SuperscriptExtension.create(),
                AutolinkExtension.create()))
        parser = Parser.builder(options).build()
        renderer = HtmlRenderer.builder(options).build()
    }

}
