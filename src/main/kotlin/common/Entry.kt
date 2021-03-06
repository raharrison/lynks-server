package common

import db.json
import group.Collection
import group.Tag
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import resource.Resources

abstract class BaseEntries(name: String) : Table(name) {
    val id = varchar("ID", 12)
    val title = varchar("TITLE", 255)
    val plainContent = text("PLAIN_CONTENT").nullable()
    val content = text("CONTENT").nullable()
    val src = varchar("SOURCE", 255)
    val type = enumeration("TYPE", EntryType::class)
    val dateCreated = long("DATE_CREATED")
    val dateUpdated = long("DATE_UPDATED")
    val props = json("PROPS", BaseProperties::class.java).nullable()
    abstract val version: Column<Int>
    val starred = bool("STARRED").default(false)
    abstract val thumbnailId: Column<String?>
}

object Entries : BaseEntries("ENTRY") {
    override val version = integer("VERSION").default(1)
    // as override to avoid cyclic foreign key issues between entries and resources
    override val thumbnailId = varchar("THUMBNAIL_ID", 12).references(Resources.id, ReferenceOption.SET_NULL).nullable()
    override val primaryKey = PrimaryKey(id)
}

object EntryVersions : BaseEntries("ENTRY_VERSION") {
    override val version = integer("VERSION").default(1)
    override val thumbnailId = varchar("THUMBNAIL_ID", 12).references(Resources.id, ReferenceOption.SET_NULL).nullable()
    override val primaryKey = PrimaryKey(id, version)
}

interface Entry : IdBasedCreatedEntity {
    val title: String
    val dateCreated: Long
    val dateUpdated: Long
    val version: Int
    val starred: Boolean
    val props: BaseProperties
    val tags: List<Tag>
    val collections: List<Collection>
    var thumbnailId: String?
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
