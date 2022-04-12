package lynks.group

import lynks.common.Entries
import lynks.common.IdBasedCreatedEntity
import lynks.common.IdBasedNewEntity
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object Groups: Table("GROUP") {
    val id = varchar("ID", 14)
    val type = enumeration("TYPE", GroupType::class).index()
    val name = varchar("NAME", 255)
    val parentId = (varchar("PARENT_ID", 14) references id).nullable().index()
    val dateCreated = long("DATE_CREATED")
    val dateUpdated = long("DATE_UPDATED")
    override val primaryKey = PrimaryKey(id)
}

object EntryGroups: Table("ENTRY_GROUP") {
    val groupId = (varchar("GROUP_ID", 14).references(Groups.id, ReferenceOption.CASCADE))
    val entryId = (varchar("ENTRY_ID", 14).references(Entries.id, ReferenceOption.CASCADE))
    override val primaryKey = PrimaryKey(groupId, entryId)
}


interface Grouping<T>: IdBasedCreatedEntity {
    var name: String
    var path: String?
    var children: MutableSet<T>
    var dateCreated: Long
    var dateUpdated: Long

    fun copy(): T
}

class GroupSet(val tags: List<Tag> = emptyList(), val collections: List<Collection> = emptyList())

data class Tag(
    override val id: String,
    override var name: String,
    override var path: String?,
    override var dateCreated: Long,
    override var dateUpdated: Long
): Grouping<Tag> {

    override var children: MutableSet<Tag> = mutableSetOf()

    override fun hashCode(): Int = id.hashCode()
    override fun equals(other: Any?): Boolean = if (other is Tag) id == other.id else false

    override fun copy(): Tag = copy(id = this.id)
}

data class NewTag(
        override val id: String? = null,
        val name: String
): IdBasedNewEntity



data class Collection(
    override val id: String,
    override var name: String,
    override var path: String?,
    override var children: MutableSet<Collection>,
    override var dateCreated: Long,
    override var dateUpdated: Long
): Grouping<Collection> {
    override fun hashCode(): Int = id.hashCode()
    override fun equals(other: Any?): Boolean = if (other is Collection) id == other.id else false

    override fun copy(): Collection = copy(id = this.id)
}

data class NewCollection(
        override val id: String? = null,
        val name: String,
        val parentId: String? = null
): IdBasedNewEntity

