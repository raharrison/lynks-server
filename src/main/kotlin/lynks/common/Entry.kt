package lynks.common

import com.fasterxml.jackson.module.kotlin.readValue
import lynks.group.Collection
import lynks.group.Tag
import lynks.resource.ResourceVersions
import lynks.util.JsonMapper
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.json.json

abstract class BaseEntries(name: String) : Table(name) {
    val id = varchar("ID", UID_LENGTH)
    val title = varchar("TITLE", 255)
    val plainContent = text("PLAIN_CONTENT").nullable()
    val content = text("CONTENT").nullable()
    val src = varchar("SOURCE", 255)
    val type = enumerationByName<EntryType>("TYPE", 8).index()
    val dateCreated = long("DATE_CREATED")
    val dateUpdated = long("DATE_UPDATED").index()
    val props = json("PROPS", { JsonMapper.defaultMapper.writeValueAsString(it) }, { JsonMapper.defaultMapper.readValue<BaseProperties>(it) }).nullable()
    abstract val version: Column<Int>
    val starred = bool("STARRED").default(false)
    val read = bool("READ").nullable()
    abstract val thumbnailId: Column<String?>
}

object Entries : BaseEntries("ENTRY") {
    override val version = integer("VERSION").default(1)
    // as override to avoid cyclic foreign key issues between entries and resources
    override val thumbnailId = varchar("THUMBNAIL_ID", UID_LENGTH).references(ResourceVersions.id, ReferenceOption.SET_NULL).nullable()
    override val primaryKey = PrimaryKey(id)
}

object EntryVersions : BaseEntries("ENTRY_VERSION") {
    override val version = integer("VERSION").default(1)
    override val thumbnailId = varchar("THUMBNAIL_ID", UID_LENGTH).references(ResourceVersions.id, ReferenceOption.SET_NULL).nullable()
    override val primaryKey = PrimaryKey(id, version)
}

interface Entry : TypedIdEntity<EntryId> {
    val type: EntryType
    val dateCreated: Long
    val dateUpdated: Long
    val version: Int
    val starred: Boolean
    val props: BaseProperties
    val tags: List<Tag>
    val collections: List<Collection>
}

interface NewEntry : NewTypedIdEntity<EntryId> {
    val tags: List<String>
    val collections: List<String>
}

data class EntryVersion(
    val id: EntryId,
    val version: Int,
    val dateUpdated: Long
)
