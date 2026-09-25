package lynks.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import lynks.auth.UserSessions
import lynks.comment.Comments
import lynks.common.Entries
import lynks.common.EntryAudit
import lynks.common.EntryVersions
import lynks.common.Environment
import lynks.digest.Digests
import lynks.entry.ref.EntryRefs
import lynks.group.EntryGroups
import lynks.group.Groups
import lynks.notify.Notifications
import lynks.reminder.Reminders
import lynks.resource.ResourceVersions
import lynks.resource.Resources
import lynks.user.Users
import lynks.util.loggerFor
import lynks.worker.WorkerSchedules
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils.create
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import javax.sql.DataSource

class DatabaseFactory {

    private val log = loggerFor<DatabaseFactory>()

    var connected: Boolean = false
        private set

    private val tables = listOf(
        Entries, EntryVersions, EntryAudit, EntryRefs,
        Comments, Resources, ResourceVersions, Reminders, Users, UserSessions,
        Groups, EntryGroups, WorkerSchedules, Notifications, Digests
    )

    fun connectAndMigrate() {
        log.info("Initialising database")

        val pool = hikari()
        Database.connect(pool)
        runFlyway(pool)

        connected = true
    }

    private fun hikari(): HikariDataSource {
        val config = HikariConfig()
        config.driverClassName = "org.postgresql.Driver"
        config.jdbcUrl = Environment.database.url
        config.username = Environment.database.user
        config.password = Environment.database.password
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
        val tableNames = tables.joinToString(", ") { "\"${it.tableName}\"" }
        exec("TRUNCATE TABLE $tableNames RESTART IDENTITY CASCADE")
    }
}
