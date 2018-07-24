package tag

import common.Entries
import common.IdBasedCreatedEntity
import common.IdBasedNewEntity
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object Tags : Table("Tag") {
    val id = varchar("id", 12).primaryKey()
    val name = varchar("name", 255)
    val parentId = (varchar("parentId", 12) references id).nullable()
    val dateUpdated = long("dateCreated")
}

object EntryTags : Table("EntryTag") {
    val tagId = (varchar("tagId", 12).references(Tags.id, ReferenceOption.CASCADE)).primaryKey()
    val entryId = (varchar("entryId", 12).references(Entries.id, ReferenceOption.CASCADE)).primaryKey()
}

data class Tag(
        override val id: String,
        var name: String,
        var children: MutableSet<Tag>,
        var dateUpdated: Long
): IdBasedCreatedEntity {
    override fun hashCode(): Int = id.hashCode()
    override fun equals(other: Any?): Boolean = if (other is Tag) id == other.id else false
}

data class NewTag(
        override val id: String?,
        val name: String,
        val parentId: String?
): IdBasedNewEntity
