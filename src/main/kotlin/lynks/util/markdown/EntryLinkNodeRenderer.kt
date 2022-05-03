package lynks.util.markdown

import com.vladsch.flexmark.html.HtmlWriter
import com.vladsch.flexmark.html.renderer.NodeRenderer
import com.vladsch.flexmark.html.renderer.NodeRendererContext
import com.vladsch.flexmark.html.renderer.NodeRendererFactory
import com.vladsch.flexmark.html.renderer.NodeRenderingHandler
import com.vladsch.flexmark.util.data.DataHolder
import lynks.entry.EntryService

internal class EntryLinkNodeRenderer(private val entryService: EntryService) : NodeRenderer {

    override fun getNodeRenderingHandlers(): Set<NodeRenderingHandler<*>> {
        return setOf(NodeRenderingHandler(EntryLinkNode::class.java) { node: EntryLinkNode, _: NodeRendererContext, html: HtmlWriter ->
            render(node, html)
        })
    }

    private fun render(node: EntryLinkNode, html: HtmlWriter) {
        val entry = entryService.get(listOf(node.text.toString())).content.singleOrNull()
        if (entry == null) {
            html.srcPos(node.chars).text(node.chars)
        } else {
            val href = "/entries/${entry.type.name.lowercase()}s/${node.text}"
            html.srcPos(node.chars).attr("href", href).withAttr().tag("a")
            html.raw("<strong>")
            html.text(node.chars)
            html.raw("</strong>")
            html.tag("/a")
        }
    }

    class Factory(private val entryService: EntryService) : NodeRendererFactory {
        override fun apply(options: DataHolder): NodeRenderer {
            return EntryLinkNodeRenderer(entryService)
        }
    }
}
