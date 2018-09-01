package group

import common.Entries
import common.IdBasedCreatedEntity
import common.IdBasedNewEntity
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table




abstract class Groups(name: String): Table(name) {
    val id = varchar("id", 12).primaryKey()
    val name = varchar("name", 255)
    val parentId = (varchar("parentId", 12) references id).nullable()
    val dateCreated = long("dateCreated")
    val dateUpdated = long("dateUpdated")
}

object Tags: Groups("Tag")
object Collections: Groups("Collection")




abstract class EntryGroups(name: String): Table(name) {
    abstract val groupId: Column<String>
    val entryId = (varchar("entryId", 12).references(Entries.id, ReferenceOption.CASCADE)).primaryKey()
}

object EntryTags : EntryGroups("EntryTag") {
    override val groupId = (varchar("tagId", 12).references(Tags.id, ReferenceOption.CASCADE)).primaryKey()
}

object EntryCollections: EntryGroups("EntryCollections") {
    override val groupId = (varchar("collectionId", 12).references(Collections.id, ReferenceOption.CASCADE)).primaryKey()
}





interface Grouping<T>: IdBasedCreatedEntity {
    var name: String
    var children: MutableSet<T>
    var dateCreated: Long
    var dateUpdated: Long

    fun copy(): T
}


data class Tag(
        override val id: String,
        override var name: String,
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

