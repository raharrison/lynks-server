package lynks.resource

import com.vladsch.flexmark.ast.Image
import com.vladsch.flexmark.ast.LinkNodeBase
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.ast.NodeVisitor
import com.vladsch.flexmark.util.ast.VisitHandler
import com.vladsch.flexmark.util.sequence.PrefixedSubSequence
import com.vladsch.flexmark.util.sequence.SegmentedSequence
import lynks.common.Environment
import lynks.common.IMAGE_UPLOAD_BASE
import lynks.common.TEMP_URL
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension

class TempImageMarkdownVisitor(
    private val eid: String,
    private val resourceManager: ResourceManager
) {

    var visitedCount = 0

    private val visitor: NodeVisitor = NodeVisitor(
        VisitHandler(Image::class.java, this::visit)
    )

    fun replaceUrl(node: Node) {
        visitor.visit(node)
    }

    private fun visit(node: Image) {
        visit(node as LinkNodeBase)
    }

    private fun visit(node: LinkNodeBase) {
        if (node.pageRef.startsWith(TEMP_URL)) {
            val file = resourceManager.constructTempBasePath(IMAGE_UPLOAD_BASE).resolve(Path.of(node.pageRef.toString()).fileName)
            val generatedResource = GeneratedResource(ResourceType.UPLOAD, file.absolutePathString(), file.extension)
            val migrated = resourceManager.migrateGeneratedResources(eid, listOf(generatedResource))
            migrated.firstOrNull()?.let {
                val newUrl = "${Environment.server.rootPath}/entry/$eid/resource/${it.id}"
                node.setUrlChars(PrefixedSubSequence.prefixOf(newUrl, node.pageRef.emptyPrefix))
                node.chars = SegmentedSequence.create(node.chars, node.segmentsForChars.toList())
                visitedCount++
            }
        }
    }

}
