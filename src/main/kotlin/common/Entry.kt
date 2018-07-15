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
}

object Entries : BaseEntries("Entry") {
    override val version = integer("version").default(0)
}

object EntryVersions: BaseEntries("EntryVersion") {
    override val version = integer("version").primaryKey().default(0)
}

interface Entry {
    val id: String
    val props: BaseProperties
}

interface NewEntry {
    val id: String?
    val tags: List<String>
}
