package comment

import common.DefaultPageRequest
import common.PageRequest
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import util.MarkdownUtils
import util.RandomUtils
import util.RowMapper.toComment

class CommentService {

    fun getComment(entryId: String, id: String): Comment? = transaction {
        Comments.select { Comments.id eq id and (Comments.entryId eq entryId) }.mapNotNull {
            toComment(it)
        }.singleOrNull()
    }

    fun getCommentsFor(id: String, pageRequest: PageRequest = DefaultPageRequest): List<Comment> = transaction {
        Comments.select { Comments.entryId eq id }
                .orderBy(Comments.dateCreated, SortOrder.DESC)
                .limit(pageRequest.limit, pageRequest.offset)
                .map { toComment(it) }
    }

    fun deleteComment(entryId: String, id: String): Boolean = transaction {
        Comments.deleteWhere { Comments.id eq id and (Comments.entryId eq entryId) } > 0
    }

    fun addComment(eId: String, comment: NewComment): Comment = transaction {
        val newId = RandomUtils.generateUid()
        Comments.insert {
            it[id] = newId
            it[entryId] = eId
            it[plainText] = comment.plainText
            it[markdownText] = MarkdownUtils.convertToMarkdown(comment.plainText)
            it[dateCreated] = System.currentTimeMillis()
        }
        getComment(eId, newId)!!
    }

    fun updateComment(entryId: String, comment: NewComment): Comment? {
        val id = comment.id
        return if (id == null) {
            addComment(entryId, comment)
        } else {
            transaction {
                val updated = Comments.update({ Comments.id eq id and (Comments.entryId eq entryId) }) {
                    it[plainText] = comment.plainText
                    it[markdownText] = MarkdownUtils.convertToMarkdown(comment.plainText)
                }
                if(updated > 0) getComment(entryId, id)!! else null
            }
        }
    }
}