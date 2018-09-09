package db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import comment.Comments
import common.ConfigMode
import common.Entries
import common.EntryVersions
import common.Environment
import group.Collections
import group.EntryCollections
import group.EntryTags
import group.Tags
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import resource.Resources
import schedule.Reminders
import user.UserPreferences
import util.loggerFor

val logger = loggerFor<DatabaseFactory>()

class DatabaseFactory {

    var connected: Boolean = false
        private set

    fun connect() {
        logger.info("Initialising database")

        if(Environment.mode == ConfigMode.TEST) {
            // no connection pooling
            Database.connect(Environment.server.database, driver = Environment.server.driver)
        } else {
            Database.connect(hikari())
        }

        transaction {
            create(Entries, EntryVersions,
                    Comments, Resources, Reminders, UserPreferences,
                    Tags, EntryTags, Collections, EntryCollections)
            enableSearch()
            enableTriggers()
        }
        connected = true
    }

    private fun hikari(): HikariDataSource {
        val config = HikariConfig()
        config.driverClassName = Environment.server.driver
        config.jdbcUrl = Environment.server.database
        config.maximumPoolSize = 3
        config.isAutoCommit = false
        config.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        config.validate()
        return HikariDataSource(config)
    }

    private fun enableTriggers() {
        val conn = TransactionManager.current().connection
        conn.createStatement().use {
            it.execute("CREATE TRIGGER IF NOT EXISTS ENTRY_VERS_INS AFTER INSERT ON ENTRY " +
                    "FOR EACH ROW CALL \"${EntryVersionTrigger::class.qualifiedName}\"")
            it.execute("CREATE TRIGGER IF NOT EXISTS ENTRY_VERS_UPD AFTER UPDATE ON ENTRY " +
                    "FOR EACH ROW CALL \"${EntryVersionTrigger::class.qualifiedName}\"")
        }
    }

    private fun enableSearch() {
        // test mode uses in-memory db
        if (Environment.mode == ConfigMode.TEST) {
            val conn = TransactionManager.current().connection
            conn.createStatement().use {
                it.execute("CREATE ALIAS IF NOT EXISTS FT_INIT FOR \"org.h2.fulltext.FullText.init\";")
                it.execute("CALL FT_INIT()")
                it.execute("CALL FT_CREATE_INDEX('PUBLIC', 'ENTRY', 'TITLE,PLAINCONTENT');")
            }
        }
    }

    fun resetAll(): Unit = transaction {
        Resources.deleteAll()
        EntryTags.deleteAll()
        Tags.deleteAll()
        EntryCollections.deleteAll()
        Collections.deleteAll()
        Comments.deleteAll()
        Reminders.deleteAll()
        Entries.deleteAll()
        EntryVersions.deleteAll()
        UserPreferences.deleteAll()
    }
}