package lynks.user

import lynks.common.EntryId
import lynks.common.EntryType
import lynks.common.UID_LENGTH
import lynks.common.UserId
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.Instant

object Users : Table("user_profiles") {
    val id = varchar("id", UID_LENGTH)
    val username = varchar("username", USERNAME_MAX_LENGTH).uniqueIndex()
    val password = varchar("password_hash", 128)
    val displayName = varchar("display_name", 64).nullable()
    val digest = bool("digest").default(false)
    val dateCreated = timestampWithTimeZone("date_created")
    val dateUpdated = timestampWithTimeZone("date_updated")
    val activated = bool("activated").default(false)
    val totp = varchar("totp", 32).nullable()
    val joltToken = varchar("jolt_token", JOLT_TOKEN_MAX_LENGTH).nullable()
    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

const val USERNAME_MAX_LENGTH = 25
const val JOLT_TOKEN_MAX_LENGTH = 128

// the authenticated caller, resolved from the session or the default user when auth is disabled
data class UserPrincipal(val id: UserId, val username: String)

data class AuthRequest(val username: String, val password: String, val totp: String? = null)
enum class AuthResult { SUCCESS, TOTP_REQUIRED, INVALID_CREDENTIALS }
data class AuthOutcome(val result: AuthResult, val userId: UserId? = null)
data class ChangePasswordRequest(val oldPassword: String, val newPassword: String)
data class UserUpdateRequest(
    val displayName: String? = null,
    val digest: Boolean = false
)

data class TwoFactorUpdateRequest(val enabled: Boolean)
data class TwoFactorValidateRequest(val code: String)
data class JoltTokenRequest(val token: String? = null)

data class User(
    val id: UserId,
    val username: String,
    val displayName: String? = null,
    val digest: Boolean = false,
    // the token itself is never returned, only whether one is set
    val joltConfigured: Boolean = false,
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
