package db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import comment.Comments
import common.Entries
import common.EntryAudit
import common.EntryVersions
import common.Environment
import group.EntryGroups
import group.Groups
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import reminder.Reminders
import resource.Resources
import user.UserPreferences
import util.loggerFor
import worker.WorkerSchedules
import javax.sql.DataSource

val log = loggerFor<DatabaseFactory>()

class DatabaseFactory {

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
