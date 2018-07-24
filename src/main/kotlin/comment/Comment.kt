package comment

import common.Entries
import common.IdBasedCreatedEntity
import common.IdBasedNewEntity
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object Comments : Table("Comment") {
    val id = varchar("id", 12).primaryKey()
    val entryId = (varchar("entryId", 12).references(Entries.id, ReferenceOption.CASCADE)).index()
    val plainText = text("plainText")
    val markdownText = text("markdownText")
    val dateCreated = long("dateCreated")
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
