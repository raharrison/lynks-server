package lynks.common

import lynks.db.json
import lynks.group.Collection
import lynks.group.Tag
import lynks.resource.ResourceVersions
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

abstract class BaseEntries(name: String) : Table(name) {
    val id = varchar("ID", 14)
    val title = varchar("TITLE", 255)
    val plainContent = text("PLAIN_CONTENT").nullable()
    val content = text("CONTENT").nullable()
    val src = varchar("SOURCE", 255)
    val type = enumeration("TYPE", EntryType::class).index()
    val dateCreated = long("DATE_CREATED")
    val dateUpdated = long("DATE_UPDATED").index()
    val props = json("PROPS", BaseProperties::class.java).nullable()
    abstract val version: Column<Int>
    val starred = bool("STARRED").default(false)
    val read = bool("READ").nullable()
    abstract val thumbnailId: Column<String?>
}

object Entries : BaseEntries("ENTRY") {
    override val version = integer("VERSION").default(1)
    // as override to avoid cyclic foreign key issues between entries and resources
    override val thumbnailId = varchar("THUMBNAIL_ID", 14).references(ResourceVersions.id, ReferenceOption.SET_NULL).nullable()
    override val primaryKey = PrimaryKey(id)
}

object EntryVersions : BaseEntries("ENTRY_VERSION") {
    override val version = integer("VERSION").default(1)
    override val thumbnailId = varchar("THUMBNAIL_ID", 14).references(ResourceVersions.id, ReferenceOption.SET_NULL).nullable()
    override val primaryKey = PrimaryKey(id, version)
}

interface Entry : IdBasedCreatedEntity {
    val type: EntryType
    val dateCreated: Long
    val dateUpdated: Long
    val version: Int
    val starred: Boolean
    val props: BaseProperties
    val tags: List<Tag>
    val collections: List<Collection>
}

interface NewEntry : IdBasedNewEntity {
    val tags: List<String>
    val collections: List<String>
}

data class EntryVersion(
    override val id: String,
    val version: Int,
    val dateUpdated: Long
) : IdBasedCreatedEntity
