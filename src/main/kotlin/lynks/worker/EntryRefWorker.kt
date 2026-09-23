package lynks.worker

import com.vladsch.flexmark.util.ast.NodeVisitor
import com.vladsch.flexmark.util.ast.VisitHandler
import com.vladsch.flexmark.util.ast.Visitor
import lynks.comment.CommentService
import lynks.common.*
import lynks.common.page.PageRequest
import lynks.entry.EntryService
import lynks.entry.ref.EntryRefService
import lynks.util.markdown.EntryLinkNode
import lynks.util.markdown.MarkdownProcessor

sealed class EntryRefWorkerRequest(val userId: UserId)
class DefaultEntryRefWorkerRequest(userId: UserId, val eid: EntryId) : EntryRefWorkerRequest(userId)
class CommentRefWorkerRequest(userId: UserId, val eid: EntryId, val cid: CommentId, val updateType: CrudType) :
    EntryRefWorkerRequest(userId)

class EntryRefWorker(
    private val markdownProcessor: MarkdownProcessor,
    private val entryRefService: EntryRefService,
    private val entryService: EntryService,
    private val commentService: CommentService
) : ChannelBasedWorker<EntryRefWorkerRequest>() {

    override suspend fun doWork(input: EntryRefWorkerRequest) {
        val (entryId: EntryId, originId: String, markdown: String) = when (input) {
            is DefaultEntryRefWorkerRequest -> {
                val entry = entryService.get(input.userId, input.eid) ?: return
                val markdown = when (entry) {
                    is Note -> entry.plainContent
                    is Snippet -> entry.plainContent
                    else -> return
                }
                Triple(entry.id, entry.id.value, markdown)
            }
            is CommentRefWorkerRequest -> {
                // the comment row is already gone by the time a delete arrives
                if (input.updateType == CrudType.DELETE) {
                    val removed = entryRefService.deleteOrigin(input.cid.value)
                    log.info("{} references removed after origin deletion", removed)
                    return
                }
                val comment = commentService.getComment(input.userId, input.eid, input.cid) ?: return
                Triple(comment.entryId, comment.id.value, comment.plainContent)
            }
        }
        val refEntries = findReferencedEntries(input.userId, markdown)
        log.info("Found {} entries referenced by origin={} and entryId={}", refEntries.size, originId, entryId)
        entryRefService.setEntryRefs(entryId, refEntries, originId)
        log.info("Successfully updated entry references for entryId={} from origin={}", entryId, originId)
    }

    // Resolved as the owner, so a mention of another user's entry never becomes a reference
    private fun findReferencedEntries(userId: UserId, markdown: String): List<String> {
        val visitor = EntryRefVisitor()
        markdownProcessor.visit(markdown, NodeVisitor(VisitHandler(EntryLinkNode::class.java, visitor)))
        val refs = visitor.referencedEntries
        return refs.chunked(25).flatMap { chunk ->
            entryService.get(userId, chunk.map { EntryId(it) }, PageRequest(1, chunk.size)).content.map { it.id.value }
        }
    }

    private class EntryRefVisitor : Visitor<EntryLinkNode> {
        val referencedEntries = mutableListOf<String>()
        override fun visit(node: EntryLinkNode) {
            referencedEntries.add(node.text.toString())
        }
    }

}
