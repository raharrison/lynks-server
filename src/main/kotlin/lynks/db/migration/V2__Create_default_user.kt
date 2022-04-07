package lynks.db.migration

import lynks.common.Environment
import lynks.user.Users
import lynks.util.HashUtils
import lynks.util.loggerFor
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

class V2__Create_default_user : BaseJavaMigration() {

    private val log = loggerFor<V2__Create_default_user>()

    override fun migrate(context: Context) {
        if (Environment.auth.defaultUserPassword == null) {
            log.info("No default user password provided, not creating default user")
            return
        }

        val username = Environment.auth.defaultUserName
        val passwordInput = Environment.auth.defaultUserPassword
        val password = if (passwordInput.length == 60 && passwordInput.startsWith("\$2a\$08")) {
            passwordInput // property is already a bcrypt hash
        } else {
            HashUtils.bcryptHash(passwordInput)
        }

        val currentTime = System.currentTimeMillis()
        transaction {
            Users.insert {
                it[this.username] = username
                it[this.password] = password
                it[this.dateCreated] = currentTime
                it[this.dateUpdated] = currentTime
            }
        }
        log.info("Default user with name '{}' created", username)

    }

}
