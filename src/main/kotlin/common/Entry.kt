package common

import db.json
import group.Collection
import group.Tag
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

abstract class BaseEntries(name: String): Table(name) {
    val id = varchar("ID", 12).primaryKey()
    val title = varchar("TITLE", 255)
    val plainContent = text("PLAIN_CONTENT").nullable()
    val content = text("CONTENT").nullable()
    val src = varchar("SOURCE", 255)
    val type = enumeration("TYPE", EntryType::class)
    val dateUpdated = long("DATE_UPDATED")
    val props = json("PROPS", BaseProperties::class.java).nullable()
    abstract val version: Column<Int>
    val starred = bool("STARRED").default(false)
}

object Entries : BaseEntries("ENTRY") {
    override val version = integer("VERSION").default(0)
}

object EntryVersions: BaseEntries("ENTRY_VERSION") {
    override val version = integer("VERSION").primaryKey().default(0)
}

interface Entry: IdBasedCreatedEntity {
    val title: String
    val dateUpdated: Long
    val version: Int
    val starred: Boolean
    val props: BaseProperties
    val tags: List<Tag>
    val collections: List<Collection>
}

interface NewEntry: IdBasedNewEntity {
    val tags: List<String>
    val collections: List<String>
}
