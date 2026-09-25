package lynks.auth

import lynks.common.Environment
import lynks.common.UserId
import lynks.user.UserPrincipal
import lynks.user.Users
import lynks.util.HashUtils
import lynks.util.RandomUtils
import lynks.util.loggerFor
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime

class SessionService(
    settings: Environment.AuthSession,
    private val clock: Clock = Clock.systemUTC()
) {

    private val log = loggerFor<SessionService>()

    val maxAge: Duration = Duration.ofDays(settings.maxDays.toLong())
    private val idle = Duration.ofDays(settings.idleDays.toLong())

    data class NewSession(val id: String, val token: String)

    fun create(userId: UserId, method: SessionMethod, userAgent: String?, ip: String?): NewSession = transaction {
        val token = RandomUtils.generateToken()
        val id = RandomUtils.generateUid()
        val now = now()
        UserSessions.insert {
            it[UserSessions.id] = id
            it[tokenHash] = HashUtils.sha256Hash(token)
            it[UserSessions.userId] = userId.value
            it[UserSessions.method] = method
            it[created] = now
            it[lastSeen] = now
            it[expiresAt] = now.plus(idle)
            it[maxExpiresAt] = now.plus(maxAge)
            it[UserSessions.userAgent] = userAgent?.take(USER_AGENT_MAX_LENGTH)
            it[UserSessions.ip] = ip?.take(IP_MAX_LENGTH)
        }
        log.info("Created {} session {} for user {}", method, id, userId)
        NewSession(id, token)
    }

    // The join means a deactivated user's sessions stop working at once. expires_at never passes
    // max_expires_at, since sliding is capped by it, so it is the only expiry to check.
    fun authenticate(token: String): UserPrincipal? = transaction {
        val now = now()
        val row = UserSessions.innerJoin(Users)
            .select(UserSessions.id, UserSessions.lastSeen, UserSessions.maxExpiresAt, Users.id, Users.username)
            .where {
                (UserSessions.tokenHash eq HashUtils.sha256Hash(token)) and (UserSessions.expiresAt greater now) and Users.activated
            }
            .singleOrNull() ?: return@transaction null

        val sessionId = row[UserSessions.id]
        // sliding only every few minutes keeps a read from becoming a write on every request
        if (Duration.between(row[UserSessions.lastSeen], now) >= TOUCH_INTERVAL) {
            val idleExpiry = now.plus(idle)
            val maxExpiry = row[UserSessions.maxExpiresAt]
            UserSessions.update({ UserSessions.id eq sessionId }) {
                it[lastSeen] = now
                it[expiresAt] = if (idleExpiry.isAfter(maxExpiry)) maxExpiry else idleExpiry
            }
        }
        UserPrincipal(UserId(row[Users.id]), row[Users.username], sessionId)
    }

    fun list(userId: UserId, currentSessionId: String?): List<SessionInfo> = transaction {
        val now = now()
        UserSessions.selectAll()
            .where { (UserSessions.userId eq userId.value) and (UserSessions.expiresAt greater now) }
            .orderBy(UserSessions.lastSeen, SortOrder.DESC)
            .map {
                SessionInfo(
                    it[UserSessions.id],
                    it[UserSessions.method],
                    it[UserSessions.created].toInstant(),
                    it[UserSessions.lastSeen].toInstant(),
                    it[UserSessions.userAgent],
                    it[UserSessions.ip],
                    it[UserSessions.id] == currentSessionId
                )
            }
    }

    fun revoke(userId: UserId, sessionId: String): Boolean = transaction {
        UserSessions.deleteWhere { (UserSessions.id eq sessionId) and (UserSessions.userId eq userId.value) } > 0
    }

    fun revokeOthers(userId: UserId, currentSessionId: String?): Int = transaction {
        UserSessions.deleteWhere {
            val owned = UserSessions.userId eq userId.value
            if (currentSessionId == null) owned else owned and (UserSessions.id neq currentSessionId)
        }
    }

    fun delete(token: String): Boolean = transaction {
        UserSessions.deleteWhere { UserSessions.tokenHash eq HashUtils.sha256Hash(token) } > 0
    }

    fun deleteExpired(): Int = transaction {
        val now = now()
        UserSessions.deleteWhere { UserSessions.expiresAt lessEq now }
    }

    private fun now(): OffsetDateTime = OffsetDateTime.now(clock)

    private companion object {
        val TOUCH_INTERVAL: Duration = Duration.ofMinutes(5)
    }
}
