package lynks.comment

import lynks.common.Entries
import lynks.common.IdBasedCreatedEntity
import lynks.common.IdBasedNewEntity
import lynks.common.UID_LENGTH
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object Comments : Table("COMMENT") {
    val id = varchar("ID", UID_LENGTH)
    val entryId = (varchar("ENTRY_ID", UID_LENGTH).references(Entries.id, ReferenceOption.CASCADE)).index()
    val plainText = text("PLAIN_TEXT")
    val markdownText = text("MARKDOWN_TEXT")
    val dateCreated = long("DATE_CREATED").index()
    val dateUpdated = long("DATE_UPDATED")
    override val primaryKey = PrimaryKey(id)
}


data class Comment(
        override val id: String,
        val entryId: String,
        val plainText: String,
        val markdownText: String,
        val dateCreated: Long,
        val dateUpdated: Long
): IdBasedCreatedEntity


data class NewComment(
        override val id: String?,
        val plainText: String
): IdBasedNewEntity
