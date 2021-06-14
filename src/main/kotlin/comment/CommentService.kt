package comment

import common.RowMapper.toComment
import common.page.DefaultPageRequest
import common.page.Page
import common.page.PageRequest
import common.page.SortDirection
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import resource.ResourceManager
import resource.TempImageMarkdownVisitor
import util.RandomUtils
import util.findColumn
import util.loggerFor
import util.markdown.MarkdownUtils
import util.orderBy
import kotlin.math.max

private val log = loggerFor<CommentService>()

class CommentService(private val resourceManager: ResourceManager) {

    fun getComment(entryId: String, id: String): Comment? = transaction {
        Comments.select { Comments.id eq id and (Comments.entryId eq entryId) }.mapNotNull {
            toComment(it)
        }.singleOrNull()
    }

    fun getCommentsFor(id: String, pageRequest: PageRequest = DefaultPageRequest): Page<Comment> = transaction {
        val sortColumn = Comments.findColumn(pageRequest.sort) ?: Comments.dateCreated
        val sortOrder = pageRequest.direction ?: SortDirection.ASC
        val baseQuery = Comments.select { Comments.entryId eq id }
        Page.of(
            baseQuery.copy()
                .orderBy(sortColumn, sortOrder)
                .limit(pageRequest.size, max(0, (pageRequest.page - 1) * pageRequest.size))
                .map { toComment(it) }, pageRequest, baseQuery.count()
        )
    }

    private fun preprocess(eid: String, comment: NewComment) : NewComment {
        val (replaced, markdown) = MarkdownUtils.visitAndReplaceNodes(comment.plainText, TempImageMarkdownVisitor(eid, resourceManager))
        if(replaced > 0) {
            return comment.copy(plainText = markdown)
        }
        return comment
    }

    fun addComment(eId: String, comment: NewComment): Comment = transaction {
        val newId = RandomUtils.generateUid()
        val processedComment = preprocess(eId, comment)
        val time = System.currentTimeMillis()
        Comments.insert {
            it[id] = newId
            it[entryId] = eId
            it[plainText] = processedComment.plainText
            it[markdownText] = MarkdownUtils.convertToMarkdown(processedComment.plainText)
            it[dateCreated] = time
            it[dateUpdated] = time
        }
        getComment(eId, newId)!!
    }

    fun updateComment(entryId: String, comment: NewComment): Comment? {
        val id = comment.id
        return if (id == null) {
            log.info("Updating comment but no id, reverting to add entry={}", entryId)
            addComment(entryId, comment)
        } else {
            transaction {
                val processedComment = preprocess(entryId, comment)
                val updated = Comments.update({ Comments.id eq id and (Comments.entryId eq entryId) }) {
                    it[plainText] = processedComment.plainText
                    it[markdownText] = MarkdownUtils.convertToMarkdown(processedComment.plainText)
                    it[dateUpdated] = System.currentTimeMillis()
                }
                if (updated > 0) getComment(entryId, id)!!
                else {
                    log.info("No rows modified when updating comment id={} entry={}", id, entryId)
                    null
                }
            }
        }
    }

    fun deleteComment(entryId: String, id: String): Boolean = transaction {
        Comments.deleteWhere { Comments.id eq id and (Comments.entryId eq entryId) } > 0
    }
}
