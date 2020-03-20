package util.markdown

import com.vladsch.flexmark.html.HtmlWriter
import com.vladsch.flexmark.html.renderer.NodeRenderer
import com.vladsch.flexmark.html.renderer.NodeRendererContext
import com.vladsch.flexmark.html.renderer.NodeRendererFactory
import com.vladsch.flexmark.html.renderer.NodeRenderingHandler
import com.vladsch.flexmark.html.renderer.NodeRenderingHandler.CustomNodeRenderer
import com.vladsch.flexmark.util.data.DataHolder
import common.Environment

internal class EntryLinkNodeRenderer : NodeRenderer {

    override fun getNodeRenderingHandlers(): Set<NodeRenderingHandler<*>> {
        return setOf(NodeRenderingHandler(
            EntryLinkNode::class.java,
            CustomNodeRenderer { node: EntryLinkNode, context: NodeRendererContext, html: HtmlWriter ->
                render(
                    node,
                    context,
                    html
                )
            }
        ))
    }

    private fun render(node: EntryLinkNode, context: NodeRendererContext, html: HtmlWriter) {
        if (context.isDoNotRenderLinks) {
            html.text(node.chars)
        } else {
            val sb = StringBuilder()
            sb.append("${Environment.server.rootPath}/entry/").append(node.text)
            html.srcPos(node.chars).attr("href", sb.toString()).withAttr().tag("a")
            html.raw("<strong>")
            html.text(node.chars)
            html.raw("</strong>")
            html.tag("/a")
        }
    }

    class Factory : NodeRendererFactory {
        override fun apply(options: DataHolder): NodeRenderer {
            return EntryLinkNodeRenderer()
        }
    }
}