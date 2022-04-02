package lynks.user

import org.jetbrains.exposed.sql.Table

object Users : Table("USER_PROFILE") {
    val username = varchar("USERNAME", 25)
    val password = char("PASSWORD_HASH", 60)
    val email = varchar("EMAIL", 100).nullable()
    val displayName = varchar("DISPLAY_NAME", 50).nullable()
    val digest = bool("DIGEST").default(false)
    override val primaryKey: PrimaryKey = PrimaryKey(username)
}

data class AuthRequest(val username: String, val password: String)
data class ChangePasswordRequest(val username: String, val oldPassword: String, val newPassword: String)
data class User(
    val username: String,
    val email: String? = null,
    val displayName: String? = null,
    val digest: Boolean = false
)
