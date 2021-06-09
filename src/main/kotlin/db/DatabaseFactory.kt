package db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import comment.Comments
import common.*
import group.EntryGroups
import group.Groups
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.statements.jdbc.JdbcConnectionImpl
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import reminder.Reminders
import resource.Resources
import user.UserPreferences
import util.loggerFor
import worker.WorkerSchedules

val log = loggerFor<DatabaseFactory>()

class DatabaseFactory {

    var connected: Boolean = false
        private set

    private val tables = listOf(
        Entries, EntryVersions, EntryAudit,
        Comments, Resources, Reminders, UserPreferences,
        Groups, EntryGroups, WorkerSchedules
    )

    fun connect() {
        log.info("Initialising database {}", Environment.database.dialect)

        if (Environment.mode == ConfigMode.TEST) {
            // no connection pooling
            log.info("In test mode, not using connection pooling")
            Database.connect(Environment.database.url, user = Environment.database.user, password = Environment.database.password)
        } else {
            Database.connect(hikari())
        }

        transaction {
            create(*tables.toTypedArray())
            if(Environment.database.dialect == DatabaseDialect.H2) {
                enableSearch()
                enableTriggers()
            }
        }
        connected = true
    }

    private fun hikari(): HikariDataSource {
        val config = HikariConfig()
        config.driverClassName = Environment.database.dialect.driver
        config.jdbcUrl = Environment.database.url
        config.username = Environment.database.user
        config.password = Environment.database.password
        config.maximumPoolSize = 3
        config.isAutoCommit = false
        config.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        config.validate()
        return HikariDataSource(config)
    }

    private fun enableTriggers() {
        val conn = (TransactionManager.current().connection as JdbcConnectionImpl).connection
        conn.createStatement().use {
            it.execute(
                "CREATE TRIGGER IF NOT EXISTS ENTRY_VERS_INS AFTER INSERT ON ENTRY " +
                        "FOR EACH ROW CALL \"${EntryVersionTrigger::class.qualifiedName}\""
            )
            it.execute(
                "CREATE TRIGGER IF NOT EXISTS ENTRY_VERS_UPD AFTER UPDATE ON ENTRY " +
                        "FOR EACH ROW CALL \"${EntryVersionTrigger::class.qualifiedName}\""
            )
        }
    }

    private fun enableSearch() {
        // execute if db file doesn't already exist, otherwise test mode uses in-memory db
        if (Environment.mode == ConfigMode.TEST) {
            val conn = (TransactionManager.current().connection as JdbcConnectionImpl).connection
            conn.createStatement().use {
                it.execute("CREATE ALIAS IF NOT EXISTS FT_INIT FOR \"org.h2.fulltext.FullText.init\";")
                it.execute("CALL FT_INIT()")
                it.execute("CALL FT_CREATE_INDEX('PUBLIC', 'ENTRY', 'TITLE,PLAIN_CONTENT');")
            }
        }
    }

    fun resetAll(): Unit = transaction {
        tables.forEach {
            it.deleteAll()
        }
    }
}
