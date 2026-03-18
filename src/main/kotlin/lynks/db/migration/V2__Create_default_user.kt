package lynks.db.migration

import lynks.common.Environment
import lynks.user.Users
import lynks.util.HashUtils
import lynks.util.loggerFor
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

class V2__Create_default_user : BaseJavaMigration() {

    private val log = loggerFor<V2__Create_default_user>()

    override fun migrate(context: Context) {
        if (Environment.auth.defaultUserPassword == null) {
            if (Environment.auth.enabled) {
                log.warn("Auth is enabled but no defaultUserPassword configured — application will start with no users. Set auth.defaultUserPassword to create the initial user.")
            } else {
                log.info("No default user password provided, not creating default user")
            }
            return
        }

        val username = Environment.auth.defaultUserName
        val passwordInput = Environment.auth.defaultUserPassword
        val bcryptPattern = Regex("""^\$2[abxy]\$\d{2}\$""")
        val password = if (bcryptPattern.containsMatchIn(passwordInput)) {
            passwordInput // property is already a bcrypt hash
        } else {
            HashUtils.bcryptHash(passwordInput)
        }

        val currentTime = OffsetDateTime.now(ZoneOffset.UTC)
        transaction {
            Users.insert {
                it[this.username] = username
                it[this.password] = password
                it[this.dateCreated] = currentTime
                it[this.dateUpdated] = currentTime
                it[this.activated] = true
            }
        }
        log.info("Default user with name '{}' created", username)

    }

}
