package lynks.util.markdown

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.html.HtmlRenderer.HtmlRendererExtension
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.parser.Parser.ParserExtension
import com.vladsch.flexmark.util.data.MutableDataHolder
import lynks.entry.EntryService

class EntryLinkExtension(private val entryService: EntryService) : ParserExtension, HtmlRendererExtension {

    override fun parserOptions(options: MutableDataHolder) {}
    override fun rendererOptions(options: MutableDataHolder) {}

    override fun extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String) {
        htmlRendererBuilder.nodeRendererFactory(EntryLinkNodeRenderer.Factory(entryService))
    }

    override fun extend(parserBuilder: Parser.Builder) {
        parserBuilder.customInlineParserExtensionFactory(EntryLinkInlineParserExtension.Factory())
    }
}
