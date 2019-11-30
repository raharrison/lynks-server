package comment

import common.Entries
import common.IdBasedCreatedEntity
import common.IdBasedNewEntity
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object Comments : Table("COMMENT") {
    val id = varchar("ID", 12).primaryKey()
    val entryId = (varchar("ENTRY_ID", 12).references(Entries.id, ReferenceOption.CASCADE)).index()
    val plainText = text("PLAIN_TEXT")
    val markdownText = text("MARKDOWN_TEXT")
    val dateCreated = long("DATE_CREATED")
}


data class Comment(
        override val id: String,
        val entryId: String,
        val plainText: String,
        val markdownText: String,
        val dateCreated: Long
): IdBasedCreatedEntity


data class NewComment(
        override val id: String?,
        val plainText: String
): IdBasedNewEntity
