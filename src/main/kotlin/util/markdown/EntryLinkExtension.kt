package util.markdown

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.html.HtmlRenderer.HtmlRendererExtension
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.parser.Parser.ParserExtension
import com.vladsch.flexmark.util.data.MutableDataHolder

class EntryLinkExtension : ParserExtension, HtmlRendererExtension {

    override fun parserOptions(options: MutableDataHolder) {}
    override fun rendererOptions(options: MutableDataHolder) {}

    override fun extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String) {
        htmlRendererBuilder.nodeRendererFactory(EntryLinkNodeRenderer.Factory())
    }

    override fun extend(parserBuilder: Parser.Builder) {
        parserBuilder.customInlineParserExtensionFactory(EntryLinkInlineParserExtension.Factory())
    }
}