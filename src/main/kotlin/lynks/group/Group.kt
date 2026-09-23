package lynks.group

import lynks.common.Entries
import lynks.common.IdBasedCreatedEntity
import lynks.common.IdBasedNewEntity
import lynks.common.UID_LENGTH
import lynks.user.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.Instant

object Groups: Table("groups") {
    val id = varchar("id", UID_LENGTH)
    val userId = varchar("user_id", UID_LENGTH).references(Users.id, ReferenceOption.CASCADE)
    val type = enumerationByName<GroupType>("type", 20)
    val name = varchar("name", 255)
    val parentId = (varchar("parent_id", UID_LENGTH) references id).nullable().index()
    val dateCreated = timestampWithTimeZone("date_created")
    val dateUpdated = timestampWithTimeZone("date_updated")
    override val primaryKey = PrimaryKey(id)

    init {
        index(false, userId, type)
    }
}

object EntryGroups: Table("entry_groups") {
    val groupId = (varchar("group_id", UID_LENGTH).references(Groups.id, ReferenceOption.CASCADE))
    val entryId = (varchar("entry_id", UID_LENGTH).references(Entries.id, ReferenceOption.CASCADE))
    override val primaryKey = PrimaryKey(groupId, entryId)
    init { index(false, entryId) }
}


interface Grouping<T>: IdBasedCreatedEntity {
    var name: String
    var path: String?
    val children: MutableSet<T>
    var dateCreated: Instant
    var dateUpdated: Instant

    fun copy(): T
}

class GroupSet(val tags: List<Tag> = emptyList(), val collections: List<Collection> = emptyList())
data class GroupIdSet(val tags: List<String> = emptyList(), val collections: List<String> = emptyList())

data class Tag(
    override val id: String,
    override var name: String,
    override var path: String?,
    override var dateCreated: Instant,
    override var dateUpdated: Instant
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
    override var dateCreated: Instant,
    override var dateUpdated: Instant
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
