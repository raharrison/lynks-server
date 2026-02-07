package lynks.comment

import lynks.common.*
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object Comments : Table("COMMENT") {
    val id = varchar("ID", UID_LENGTH)
    val entryId = (varchar("ENTRY_ID", UID_LENGTH).index().references(Entries.id, ReferenceOption.CASCADE))
    val plainText = text("PLAIN_TEXT")
    val markdownText = text("MARKDOWN_TEXT")
    val dateCreated = long("DATE_CREATED").index()
    val dateUpdated = long("DATE_UPDATED")
    override val primaryKey = PrimaryKey(id)
}


data class Comment(
        override val id: CommentId,
        val entryId: EntryId,
        val plainText: String,
        val markdownText: String,
        val dateCreated: Long,
        val dateUpdated: Long
): TypedIdEntity<CommentId>


data class NewComment(
        override val id: CommentId?,
        val plainText: String
): NewTypedIdEntity<CommentId>
