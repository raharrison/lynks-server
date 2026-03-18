package lynks.comment

import lynks.common.*
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.Instant

object Comments : Table("comments") {
    val id = varchar("id", UID_LENGTH)
    val entryId = (varchar("entry_id", UID_LENGTH).index().references(Entries.id, ReferenceOption.CASCADE))
    val plainContent = text("plain_content")
    val renderedContent = text("rendered_content")
    val dateCreated = timestampWithTimeZone("date_created").index()
    val dateUpdated = timestampWithTimeZone("date_updated")
    override val primaryKey = PrimaryKey(id)
}


data class Comment(
        override val id: CommentId,
        val entryId: EntryId,
        val plainContent: String,
        val renderedContent: String,
        val dateCreated: Instant,
        val dateUpdated: Instant
): TypedIdEntity<CommentId>


data class NewComment(
        override val id: CommentId?,
        val plainContent: String
): NewTypedIdEntity<CommentId>
