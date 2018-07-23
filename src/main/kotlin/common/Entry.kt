package common

import db.json
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

abstract class BaseEntries(name: String): Table(name) {
    val id = varchar("id", 12).primaryKey()
    val title = varchar("title", 255)
    val plainContent = text("plainContent").nullable()
    val content = text("content").nullable()
    val src = varchar("source", 255)
    val type = enumeration("type", EntryType::class.java)
    val dateUpdated = long("dateUpdated")
    val props = json("props", BaseProperties::class.java).nullable()
    abstract val version: Column<Int>
    val starred = bool("starred").default(false)
}

object Entries : BaseEntries("Entry") {
    override val version = integer("version").default(0)
}

object EntryVersions: BaseEntries("EntryVersion") {
    override val version = integer("version").primaryKey().default(0)
}

interface Entry: IdBasedEntity {
    override val id: String
    val dateUpdated: Long
    val version: Int
    val starred: Boolean
    val props: BaseProperties
}

interface NewEntry: IdBasedEntity {
    val tags: List<String>
}
