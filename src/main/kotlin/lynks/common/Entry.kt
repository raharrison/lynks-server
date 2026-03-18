package lynks.common

import com.fasterxml.jackson.module.kotlin.readValue
import lynks.group.Collection
import lynks.group.Tag
import lynks.resource.ResourceVersions
import lynks.util.JsonMapper
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb
import java.time.Instant

abstract class BaseEntries(name: String) : Table(name) {
    val id = varchar("id", UID_LENGTH)
    val title = varchar("title", 255).nullable()
    val plainContent = text("plain_content").nullable()
    val content = text("content").nullable()
    val src = text("source").nullable()
    val type = enumerationByName<EntryType>("type", 20).index()
    val dateCreated = timestampWithTimeZone("date_created")
    val dateUpdated = timestampWithTimeZone("date_updated").index()
    val props = jsonb("props", { JsonMapper.defaultMapper.writeValueAsString(it) }, { JsonMapper.defaultMapper.readValue<BaseProperties>(it) }).nullable()
    abstract val version: Column<Int>
    val starred = bool("starred").default(false)
    val read = bool("read").nullable()
    abstract val thumbnailId: Column<String?>
}

object Entries : BaseEntries("entries") {
    override val version = integer("version").default(1)
    // as override to avoid cyclic foreign key issues between entries and resources
    override val thumbnailId = varchar("thumbnail_id", UID_LENGTH).references(ResourceVersions.id, ReferenceOption.SET_NULL).nullable().index()
    override val primaryKey = PrimaryKey(id)
}

object EntryVersions : BaseEntries("entry_versions") {
    override val version = integer("version").default(1)
    override val thumbnailId = varchar("thumbnail_id", UID_LENGTH).references(ResourceVersions.id, ReferenceOption.SET_NULL).nullable().index()
    override val primaryKey = PrimaryKey(id, version)
    init { index(false, id) }
}

interface Entry : TypedIdEntity<EntryId> {
    val type: EntryType
    val dateCreated: Instant
    val dateUpdated: Instant
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
    val dateUpdated: Instant
)
