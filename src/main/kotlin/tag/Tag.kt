package tag

import model.Entries
import org.jetbrains.exposed.sql.Table

object Tags : Table() {
    val id = varchar("id", 12).primaryKey()
    val name = varchar("name", 255)
    val parentId = (varchar("parentId", 12) references id).nullable()
    val dateUpdated = long("dateCreated")
}

object EntryTags : Table() {
    val tagId = (varchar("tagId", 12) references Tags.id).primaryKey()
    val entryId = (varchar("entryId", 12) references Entries.id).primaryKey()
}

data class Tag(
        val id: String,
        var name: String,
        var children: MutableSet<Tag>,
        var dateUpdated: Long
) {
    override fun hashCode(): Int = id.hashCode()
    override fun equals(other: Any?): Boolean = if (other is Tag) id == other.id else false
}

data class NewTag(
        val id: String?,
        val name: String,
        val parentId: String?
)
