package lynks.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import lynks.comment.Comments
import lynks.common.Entries
import lynks.common.EntryAudit
import lynks.common.EntryVersions
import lynks.common.Environment
import lynks.group.EntryGroups
import lynks.group.Groups
import lynks.reminder.Reminders
import lynks.resource.Resources
import lynks.user.UserPreferences
import lynks.util.loggerFor
import lynks.worker.WorkerSchedules
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import javax.sql.DataSource

class DatabaseFactory {

    private val log = loggerFor<DatabaseFactory>()

    var connected: Boolean = false
        private set

    private val tables = listOf(
        Entries, EntryVersions, EntryAudit,
        Comments, Resources, Reminders, UserPreferences,
        Groups, EntryGroups, WorkerSchedules
    )

    fun connectAndMigrate() {
        log.info("Initialising database with dialect: {}", Environment.database.dialect)

        val pool = hikari()
        Database.connect(pool)
        runFlyway(pool)

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

    private fun runFlyway(datasource: DataSource) {
        log.info("Flyway migration has started")
        val flyway = Flyway.configure()
            .locations("classpath:lynks/db/migration")
            .dataSource(datasource)
            .load()
        try {
            flyway.migrate()
        } catch (e: Exception) {
            log.error("Exception running flyway migration", e)
            throw e
        }
        log.info("Flyway migration has finished")
    }

    fun createAll(): Unit = transaction {
        create(*tables.toTypedArray())
    }

    fun resetAll(): Unit = transaction {
        tables.forEach {
            it.deleteAll()
        }
    }
}
