package comment

import common.PageRequest
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import util.MarkdownUtils
import util.RandomUtils
import util.RowMapper.toComment

class CommentService {

    fun getComment(id: String): Comment? = transaction {
        Comments.select { Comments.id eq id }.mapNotNull {
            toComment(it)
        }.singleOrNull()
    }

    fun getCommentsFor(id: String, pageRequest: PageRequest): List<Comment> = transaction {
        Comments.select { Comments.entryId eq id }
                .orderBy(Comments.dateCreated, false)
                .limit(pageRequest.limit, pageRequest.offset)
                .map { toComment(it) }
    }

    fun deleteComment(id: String): Boolean = transaction {
        Comments.deleteWhere { Comments.id eq id } > 0
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
        getComment(newId)!!
    }

    fun updateComment(entryId: String, comment: NewComment): Comment {
        val id = comment.id
        return if (id == null) {
            addComment(entryId, comment)
        } else {
            transaction {
                Comments.update({ Comments.id eq id }) {
                    it[plainText] = comment.plainText
                    it[markdownText] = MarkdownUtils.convertToMarkdown(comment.plainText)
                }
                getComment(id)!!
            }
        }
    }
}