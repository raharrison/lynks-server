package lynks.auth

import lynks.common.UID_LENGTH
import lynks.user.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.Instant

object UserSessions : Table("user_sessions") {
    val id = varchar("id", UID_LENGTH)

    // only the hash is stored, so a copy of the database holds no usable session
    val tokenHash = char("token_hash", 64).uniqueIndex()
    val userId = varchar("user_id", UID_LENGTH).references(Users.id, ReferenceOption.CASCADE).index()
    val method = enumerationByName<SessionMethod>("method", 16)
    val created = timestampWithTimeZone("created")
    val lastSeen = timestampWithTimeZone("last_seen")
    val expiresAt = timestampWithTimeZone("expires_at")
    val maxExpiresAt = timestampWithTimeZone("max_expires_at")
    val userAgent = varchar("user_agent", USER_AGENT_MAX_LENGTH).nullable()
    val ip = varchar("ip", IP_MAX_LENGTH).nullable()
    override val primaryKey = PrimaryKey(id)
}

const val USER_AGENT_MAX_LENGTH = 255
const val IP_MAX_LENGTH = 45

enum class SessionMethod { PASSWORD, OIDC }

data class SessionInfo(
    val id: String,
    val method: SessionMethod,
    val created: Instant,
    val lastSeen: Instant,
    val userAgent: String?,
    val ip: String?,
    val current: Boolean
)
