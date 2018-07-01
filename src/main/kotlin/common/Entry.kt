package common

import db.json
import org.jetbrains.exposed.sql.Table

object Entries : Table("Entry") {

    val id = varchar("id", 12).primaryKey()
    val title = varchar("title", 255)
    val plainContent = text("plainContent").nullable()
    val content = text("content").nullable()
    val src = varchar("source", 255)
    val type = enumeration("type", EntryType::class.java)
    val dateUpdated = long("dateUpdated")
    val props = json("props", BaseProperties::class.java).nullable()
}

interface Entry {
    val id: String
    val props: BaseProperties
}

interface NewEntry {
    val id: String?
    val tags: List<String>
}

