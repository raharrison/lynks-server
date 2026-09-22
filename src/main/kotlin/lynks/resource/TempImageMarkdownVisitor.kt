package lynks.resource

import com.vladsch.flexmark.ast.Image
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.ast.NodeVisitor
import com.vladsch.flexmark.util.ast.VisitHandler
import com.vladsch.flexmark.util.sequence.PrefixedSubSequence
import com.vladsch.flexmark.util.sequence.SegmentedSequence
import lynks.common.*
import lynks.util.loggerFor
import java.nio.file.Path

class TempImageMarkdownVisitor(
    private val eid: EntryId,
    private val resourceManager: ResourceManager
) {

    private val log = loggerFor<TempImageMarkdownVisitor>()

    val pending = mutableListOf<PendingResource>()

    // The same image can be referenced more than once, and it can only be moved once
    private val reserved = mutableMapOf<Path, ResourceId>()

    private val visitor: NodeVisitor = NodeVisitor(
        VisitHandler(Image::class.java, this::visit)
    )

    fun replaceUrl(node: Node) {
        visitor.visit(node)
    }

    private fun visit(node: Image) {
        val ref = node.pageRef.toString()
        if (!ref.startsWith(TEMP_UPLOAD_URL)) return

        val file = resourceManager.findTempUpload(ref.removePrefix(TEMP_UPLOAD_URL)) ?: run {
            log.warn("Temporary image for entry={} at {} does not exist", eid, ref)
            return
        }

        val id = reserved.getOrPut(file) {
            newResourceId().also { pending.add(PendingResource(it, ResourceType.UPLOAD, file)) }
        }

        val newUrl = "${Environment.server.rootPath}/entry/$eid/resource/$id"
        node.setUrlChars(PrefixedSubSequence.prefixOf(newUrl, node.pageRef.emptyPrefix))
        node.chars = SegmentedSequence.create(node.chars, node.segmentsForChars.toList())
    }

}
