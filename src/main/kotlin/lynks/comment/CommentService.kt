package lynks.comment

import lynks.common.CommentId
import lynks.common.EntryId
import lynks.common.RowMapper.toComment
import lynks.common.newCommentId
import lynks.common.page.DefaultPageRequest
import lynks.common.page.Page
import lynks.common.page.PageRequest
import lynks.common.page.SortDirection
import lynks.util.findColumn
import lynks.util.loggerFor
import lynks.util.markdown.MarkdownProcessor
import lynks.util.orderBy
import lynks.worker.CrudType
import lynks.worker.WorkerRegistry
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.math.max

private val log = loggerFor<CommentService>()

class CommentService(private val workerRegistry: WorkerRegistry, private val markdownProcessor: MarkdownProcessor) {

    fun getComment(entryId: EntryId, id: CommentId): Comment? = transaction {
        Comments.selectAll().where { Comments.id eq id.value and (Comments.entryId eq entryId.value) }.mapNotNull {
            toComment(it)
        }.singleOrNull()
    }

    fun getCommentsFor(id: EntryId, pageRequest: PageRequest = DefaultPageRequest): Page<Comment> = transaction {
        val sortColumn = Comments.findColumn(pageRequest.sort) ?: Comments.dateCreated
        val sortOrder = pageRequest.direction ?: SortDirection.ASC
        val baseQuery = Comments.selectAll().where { Comments.entryId eq id.value }
        Page.of(
            baseQuery.copy()
                .orderBy(sortColumn, sortOrder)
                .limit(pageRequest.size)
                .offset(max(0, (pageRequest.page - 1) * pageRequest.size))
                .map { toComment(it) }, pageRequest, baseQuery.count()
        )
    }

    fun addComment(eId: EntryId, comment: NewComment): Comment = transaction {
        val newId = newCommentId()
        val time = System.currentTimeMillis()
        val (_, processedText, html) = markdownProcessor.convertAndProcess(comment.plainText, eId)
        Comments.insert {
            it[id] = newId.value
            it[entryId] = eId.value
            it[plainText] = processedText
            it[markdownText] = html
            it[dateCreated] = time
            it[dateUpdated] = time
        }
        workerRegistry.acceptCommentRefWork(eId, newId, CrudType.CREATE)
        getComment(eId, newId)
            ?: throw IllegalStateException("Comment ${newId.value} not found after insert")
    }

    fun updateComment(entryId: EntryId, comment: NewComment): Comment? {
        val id = comment.id
        return if (id == null) {
            log.info("Updating comment but no id, reverting to add entry={}", entryId.value)
            addComment(entryId, comment)
        } else {
            transaction {
                val (_, processedText, html) = markdownProcessor.convertAndProcess(comment.plainText, entryId)
                val updated = Comments.update({ Comments.id eq id.value and (Comments.entryId eq entryId.value) }) {
                    it[plainText] = processedText
                    it[markdownText] = html
                    it[dateUpdated] = System.currentTimeMillis()
                }
                if (updated > 0) {
                    workerRegistry.acceptCommentRefWork(entryId, id, CrudType.UPDATE)
                    getComment(entryId, id)
                } else {
                    log.info("No rows modified when updating comment id={} entry={}", id, entryId.value)
                    null
                }
            }
        }
    }

    fun deleteComment(entryId: EntryId, id: CommentId): Boolean = transaction {
        val deleted = Comments.deleteWhere { Comments.id eq id.value and (Comments.entryId eq entryId.value) }
        if (deleted > 0) {
            workerRegistry.acceptCommentRefWork(entryId, id, CrudType.DELETE)
        }
        deleted > 0
    }
}
