package lynks.comment

import lynks.common.*
import lynks.common.RowMapper.toComment
import lynks.common.page.DefaultPageRequest
import lynks.common.page.Page
import lynks.common.page.PageRequest
import lynks.common.page.SortDirection
import lynks.db.EntryOwnership
import lynks.util.findColumn
import lynks.util.loggerFor
import lynks.util.markdown.MarkdownProcessor
import lynks.util.orderBy
import lynks.worker.CrudType
import lynks.worker.WorkerRegistry
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.max

private val log = loggerFor<CommentService>()

class CommentService(private val workerRegistry: WorkerRegistry, private val markdownProcessor: MarkdownProcessor) {

    private fun owned(userId: UserId, entryId: EntryId): Op<Boolean> =
        (Comments.entryId eq entryId.value) and (Entries.userId eq userId.value)

    fun getComment(userId: UserId, entryId: EntryId, id: CommentId): Comment? = transaction {
        Comments.innerJoin(Entries).select(Comments.columns)
            .where { (Comments.id eq id.value) and owned(userId, entryId) }
            .mapNotNull { toComment(it) }
            .singleOrNull()
    }

    fun getCommentsFor(userId: UserId, id: EntryId, pageRequest: PageRequest = DefaultPageRequest): Page<Comment> = transaction {
        val sortColumn = Comments.findColumn(pageRequest.sort) ?: Comments.dateCreated
        val sortOrder = pageRequest.direction ?: SortDirection.ASC
        val baseQuery = Comments.innerJoin(Entries).select(Comments.columns).where { owned(userId, id) }
        Page.of(
            baseQuery.copy()
                .orderBy(sortColumn, sortOrder)
                .limit(pageRequest.size)
                .offset(max(0, (pageRequest.page - 1) * pageRequest.size))
                .map { toComment(it) }, pageRequest, baseQuery.count()
        )
    }

    fun addComment(userId: UserId, eId: EntryId, comment: NewComment): Comment? = transaction {
        if (!EntryOwnership.isOwner(userId, eId)) return@transaction null
        val newId = newCommentId()
        val time = OffsetDateTime.now(ZoneOffset.UTC)
        val (processedText, html) = markdownProcessor.convertAndProcess(userId, comment.plainContent, eId)
        Comments.insert {
            it[id] = newId.value
            it[entryId] = eId.value
            it[plainContent] = processedText
            it[renderedContent] = html
            it[dateCreated] = time
            it[dateUpdated] = time
        }
        workerRegistry.acceptCommentRefWork(userId, eId, newId, CrudType.CREATE)
        getComment(userId, eId, newId)
            ?: throw IllegalStateException("Comment ${newId.value} not found after insert")
    }

    fun updateComment(userId: UserId, entryId: EntryId, comment: NewComment): Comment? {
        val id = comment.id
        return if (id == null) {
            log.info("Updating comment but no id, reverting to add entry={}", entryId.value)
            addComment(userId, entryId, comment)
        } else {
            transaction {
                // processing attaches pasted images to the entry, so ownership is checked first
                if (getComment(userId, entryId, id) == null) {
                    log.info("No comment found to update id={} entry={}", id, entryId.value)
                    return@transaction null
                }
                val (processedText, html) = markdownProcessor.convertAndProcess(userId, comment.plainContent, entryId)
                Comments.update({ Comments.id eq id.value and (Comments.entryId eq entryId.value) }) {
                    it[plainContent] = processedText
                    it[renderedContent] = html
                    it[dateUpdated] = OffsetDateTime.now(ZoneOffset.UTC)
                }
                workerRegistry.acceptCommentRefWork(userId, entryId, id, CrudType.UPDATE)
                getComment(userId, entryId, id)
            }
        }
    }

    fun deleteComment(userId: UserId, entryId: EntryId, id: CommentId): Boolean = transaction {
        if (getComment(userId, entryId, id) == null) return@transaction false
        val deleted = Comments.deleteWhere { Comments.id eq id.value and (Comments.entryId eq entryId.value) }
        if (deleted > 0) {
            workerRegistry.acceptCommentRefWork(userId, entryId, id, CrudType.DELETE)
        }
        deleted > 0
    }
}
