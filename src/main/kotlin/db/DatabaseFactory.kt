package db

import comment.Comments
import common.Entries
import common.EntryType
import common.Environment
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import resource.Resources
import schedule.ScheduledJobs
import tag.EntryTags
import tag.Tags
import util.loggerFor

val logger = loggerFor<DatabaseFactory>()

class DatabaseFactory {

    var connected = false

    fun connect() {
        logger.info("Initialising database")
        Database.connect(Environment.database, driver = Environment.driver)
        transaction {
            create(Entries, Comments, Tags, EntryTags, Resources, ScheduledJobs)
            Entries.insert {
                it[id] = "3kf92nf304"
                it[title] = "link title"
                it[plainContent] = "gmail.com/something"
                it[src] = "gmail.com"
                it[type] = EntryType.LINK
                it[dateUpdated] = System.currentTimeMillis()
            }
        }
        connected = true
    }

    fun resetAll(): Unit = transaction {
        Resources.deleteAll()
        EntryTags.deleteAll()
        Tags.deleteAll()
        Comments.deleteAll()
        ScheduledJobs.deleteAll()
        Entries.deleteAll()
    }
}