package comment

import common.Entries
import org.jetbrains.exposed.sql.Table

object Comments : Table("Comment") {
    val id = varchar("id", 12).primaryKey()
    val entryId = (varchar("entryId", 12) references Entries.id).index()
    val plainText = text("plainText")
    val markdownText = text("markdownText")
    val dateCreated = long("dateCreated")
}


data class Comment(
        val id: String,
        val entryId: String,
        val plainText: String,
        val markdownText: String,
        val dateCreated: Long
)


data class NewComment(
        val id: String?,
        val plainText: String
)
