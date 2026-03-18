package lynks.user

import lynks.common.EntryId
import lynks.common.EntryType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.Instant

object Users : Table("user_profiles") {
    val username = varchar("username", 25)
    val password = varchar("password_hash", 128)
    val email = varchar("email", 254).nullable()
    val displayName = varchar("display_name", 64).nullable()
    val digest = bool("digest").default(false)
    val dateCreated = timestampWithTimeZone("date_created")
    val dateUpdated = timestampWithTimeZone("date_updated")
    val activated = bool("activated").default(false)
    val totp = varchar("totp", 32).nullable()
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
    val dateCreated: Instant,
    val dateUpdated: Instant
)

data class ActivityLogItem(
    val id: String,
    val entryId: EntryId,
    val src: String?,
    val details: String,
    val entryType: EntryType,
    val entryTitle: String?,
    val timestamp: Instant
)
