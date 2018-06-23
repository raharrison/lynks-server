package db

import comment.Comments
import common.ConfigMode
import common.Entries
import common.Environment
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
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
            enableSearch()
        }
        connected = true
    }

    private fun enableSearch() {
        // test mode uses in-memory db
        if (Environment.mode == ConfigMode.TEST) {
            val conn = TransactionManager.current().connection
            conn.createStatement().use {
                it.execute("CREATE ALIAS IF NOT EXISTS FT_INIT FOR \"org.h2.fulltext.FullText.init\";")
                it.execute("CALL FT_INIT()")
                it.execute("CALL FT_CREATE_INDEX('PUBLIC', 'ENTRIES', 'TITLE,PLAINCONTENT');")
            }
        }
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