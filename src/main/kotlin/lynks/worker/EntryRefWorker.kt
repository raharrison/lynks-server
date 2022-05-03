package lynks.worker

import com.vladsch.flexmark.util.ast.NodeVisitor
import com.vladsch.flexmark.util.ast.VisitHandler
import com.vladsch.flexmark.util.ast.Visitor
import lynks.comment.CommentService
import lynks.common.Note
import lynks.common.Snippet
import lynks.common.page.PageRequest
import lynks.entry.EntryService
import lynks.entry.ref.EntryRefService
import lynks.util.markdown.EntryLinkNode
import lynks.util.markdown.MarkdownProcessor

sealed class EntryRefWorkerRequest
class DefaultEntryRefWorkerRequest(val eid: String) : EntryRefWorkerRequest()
class CommentRefWorkerRequest(val eid: String, val cid: String, val updateType: CrudType) : EntryRefWorkerRequest()

class EntryRefWorker(
    private val markdownProcessor: MarkdownProcessor,
    private val entryRefService: EntryRefService,
    private val entryService: EntryService,
    private val commentService: CommentService
) : ChannelBasedWorker<EntryRefWorkerRequest>() {

    override suspend fun doWork(input: EntryRefWorkerRequest) {
        val (entryId, originId, markdown) = when (input) {
            is DefaultEntryRefWorkerRequest -> {
                val entry = entryService.get(input.eid) ?: return
                val markdown = when (entry) {
                    is Note -> entry.plainText
                    is Snippet -> entry.plainText
                    else -> return
                }
                Triple(entry.id, entry.id, markdown)
            }
            is CommentRefWorkerRequest -> {
                val comment = commentService.getComment(input.eid, input.cid) ?: return
                if (input.updateType == CrudType.DELETE) {
                    val removed = entryRefService.deleteOrigin(comment.id)
                    log.info("{} references removed after origin deletion", removed)
                }
                Triple(comment.entryId, comment.id, comment.plainText)
            }
        }
        val refEntries = findReferencedEntries(markdown)
        log.info("Found {} entries referenced by origin={} and entryId={}", refEntries.size, originId, entryId)
        entryRefService.setEntryRefs(entryId, refEntries, originId)
        log.info("Successfully updated entry references for entryId={} from origin={}", entryId, originId)
    }

    private fun findReferencedEntries(markdown: String): List<String> {
        val visitor = EntryRefVisitor()
        markdownProcessor.visit(markdown, NodeVisitor(VisitHandler(EntryLinkNode::class.java, visitor)))
        val refs = visitor.referencedEntries
        return refs.chunked(25).flatMap { chunk ->
            entryService.get(chunk, PageRequest(1, chunk.size)).content.map { it.id }
        }
    }

    private class EntryRefVisitor : Visitor<EntryLinkNode> {
        val referencedEntries = mutableListOf<String>()
        override fun visit(node: EntryLinkNode) {
            referencedEntries.add(node.text.toString())
        }
    }

}
