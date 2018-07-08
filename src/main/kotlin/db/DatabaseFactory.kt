package db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
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
import schedule.Schedules
import tag.EntryTags
import tag.Tags
import util.loggerFor

val logger = loggerFor<DatabaseFactory>()

class DatabaseFactory {

    var connected: Boolean = false
        private set

    fun connect() {
        logger.info("Initialising database")

        if(Environment.mode == ConfigMode.TEST) {
            // no connection pooling
            Database.connect(Environment.database, driver = Environment.driver)
        } else {
            Database.connect(hikari())
        }

        transaction {
            create(Entries, Comments, Tags, EntryTags, Resources, Schedules)
            enableSearch()
        }
        connected = true
    }

    private fun hikari(): HikariDataSource {
        val config = HikariConfig()
        config.driverClassName = Environment.driver
        config.jdbcUrl = Environment.database
        config.maximumPoolSize = 3
        config.isAutoCommit = false
        config.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        config.validate()
        return HikariDataSource(config)
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
        Comments.deleteAll()
        Schedules.deleteAll()
        Entries.deleteAll()
    }
}