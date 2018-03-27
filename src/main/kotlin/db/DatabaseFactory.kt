package db

import comment.Comments
import common.Entries
import common.EntryType
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import tag.EntryTags
import tag.Tags

class DatabaseFactory {
    init {
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction {
            create(Entries, Comments, Tags, EntryTags)
            Entries.insert {
                it[id] = "3kf92nf304"
                it[title] = "link title"
                it[plainContent] = "gmail.com/something"
                it[src] = "gmail.com"
                it[type] = EntryType.LINK
                it[dateUpdated] = System.currentTimeMillis()
            }
        }
    }
}