package lynks.user

import lynks.common.EntryId
import lynks.common.EntryType
import org.jetbrains.exposed.v1.core.Table

object Users : Table("USER_PROFILE") {
    val username = varchar("USERNAME", 25)
    val password = varchar("PASSWORD_HASH", 128)
    val email = varchar("EMAIL", 254).nullable()
    val displayName = varchar("DISPLAY_NAME", 64).nullable()
    val digest = bool("DIGEST").default(false)
    val dateCreated = long("DATE_CREATED")
    val dateUpdated = long("DATE_UPDATED")
    val activated = bool("ACTIVATED").default(false)
    val totp = varchar("TOTP", 32).nullable()
    override val primaryKey: PrimaryKey = PrimaryKey(username)
}

data class AuthRequest(val username: String, val password: String, val totp: String? = null)
enum class AuthResult { SUCCESS, TOTP_REQUIRED, INVALID_CREDENTIALS }
data class ChangePasswordRequest(val username: String, val oldPassword: String, val newPassword: String)
data class UserUpdateRequest(
    val username: String,
    val email: String? = null,
    val displayName: String? = null,
    val digest: Boolean = false
)

data class TwoFactorUpdateRequest(val enabled: Boolean)
data class TwoFactorValidateRequest(val code: String)

data class User(
    val username: String,
    val email: String? = null,
    val displayName: String? = null,
    val digest: Boolean = false,
    val dateCreated: Long,
    val dateUpdated: Long
)

data class ActivityLogItem(
    val id: String,
    val entryId: EntryId,
    val src: String?,
    val details: String,
    val entryType: EntryType,
    val entryTitle: String,
    val timestamp: Long
)
