package lynks.user

import lynks.common.*
import lynks.common.exception.InvalidModelException
import lynks.common.page.Page
import lynks.common.page.PageRequest
import lynks.common.page.SortDirection
import lynks.util.HashUtils
import lynks.util.RandomUtils
import lynks.util.loggerFor
import lynks.util.orderBy
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.max

class UserService(private val twoFactorService: TwoFactorService) {

    private val log = loggerFor<UserService>()
    private val userColumns = Users.columns - Users.password - Users.totp
    private val activityLogColumns = EntryAudit.columns + listOf(Entries.type, Entries.title)

    private fun toUser(row: ResultRow) = User(
        UserId(row[Users.id]),
        row[Users.username],
        row[Users.displayName],
        row[Users.digest],
        row[Users.joltToken] != null,
        row[Users.dateCreated].toInstant(),
        row[Users.dateUpdated].toInstant()
    )

    fun getUser(userId: UserId): User? = transaction {
        Users.select(userColumns).where { (Users.id eq userId.value) and Users.activated }
            .map { toUser(it) }
            .singleOrNull()
    }

    fun getActiveUsers(): List<User> = transaction {
        Users.select(userColumns).where { Users.activated eq true }.map { toUser(it) }
    }

    fun getPrincipal(userId: UserId): UserPrincipal? = transaction {
        Users.select(Users.id, Users.username).where { (Users.id eq userId.value) and Users.activated }
            .map { UserPrincipal(UserId(it[Users.id]), it[Users.username]) }
            .singleOrNull()
    }

    fun getPrincipal(username: String): UserPrincipal? = transaction {
        Users.select(Users.id, Users.username).where { (Users.username eq username) and Users.activated }
            .map { UserPrincipal(UserId(it[Users.id]), it[Users.username]) }
            .singleOrNull()
    }

    private fun createUser(username: String, passwordHash: String, activated: Boolean): UserId = transaction {
        if (!Users.selectAll().where { Users.username eq username }.empty()) {
            throw InvalidModelException("User with that name already exists")
        }
        val id = newUserId()
        val currentTime = OffsetDateTime.now(ZoneOffset.UTC)
        Users.insert {
            it[Users.id] = id.value
            it[Users.username] = username
            it[password] = passwordHash
            it[dateCreated] = currentTime
            it[dateUpdated] = currentTime
            it[Users.activated] = activated
        }
        id
    }

    // With auth disabled every request acts as the default user, so it has to exist even without a password
    fun ensureDefaultUser() {
        val username = Environment.auth.defaultUserName
        val configuredPassword = Environment.auth.defaultUserPassword
        val (exists, activated, noUsers) = transaction {
            val row = Users.select(Users.activated).where { Users.username eq username }.singleOrNull()
            Triple(row != null, row?.get(Users.activated) ?: false, Users.selectAll().empty())
        }

        if (exists) {
            if (!Environment.auth.enabled && !activated) {
                log.warn("Auth is disabled but default user '{}' is deactivated, so every request will be rejected", username)
            }
            return
        }

        if (Environment.auth.enabled) {
            if (!noUsers) return
            if (configuredPassword == null) {
                log.warn("Auth is enabled but no users exist. Set auth.defaultUserPassword or create one with scripts/manage_users.py")
                return
            }
        }

        val passwordHash = when {
            configuredPassword == null -> HashUtils.bcryptHash(RandomUtils.generateUuid64())
            BCRYPT_PATTERN.containsMatchIn(configuredPassword) -> configuredPassword
            else -> HashUtils.bcryptHash(configuredPassword)
        }
        createUser(username, passwordHash, activated = true)
        log.info("Default user with name '{}' created", username)
    }

    fun updateUser(userId: UserId, userUpdate: UserUpdateRequest): User? = transaction {
        val updated = Users.update({ (Users.id eq userId.value) and Users.activated }) {
            it[displayName] = userUpdate.displayName
            it[digest] = userUpdate.digest
            it[dateUpdated] = OffsetDateTime.now(ZoneOffset.UTC)
        }
        if (updated > 0) getUser(userId) else null
    }

    fun getJoltToken(userId: UserId): String? = transaction {
        Users.select(Users.joltToken).where { (Users.id eq userId.value) and Users.activated }
            .map { it[Users.joltToken] }
            .singleOrNull()
    }

    // blank clears the token; it becomes a url path segment, so only url safe characters are accepted
    fun updateJoltToken(userId: UserId, token: String?): User? = transaction {
        val cleaned = token?.trim()?.ifEmpty { null }
        if (cleaned != null && !JOLT_TOKEN_PATTERN.matches(cleaned)) {
            throw InvalidModelException("Jolt token must be up to $JOLT_TOKEN_MAX_LENGTH letters, digits, '_' or '-'")
        }
        val updated = Users.update({ (Users.id eq userId.value) and Users.activated }) {
            it[joltToken] = cleaned
            it[dateUpdated] = OffsetDateTime.now(ZoneOffset.UTC)
        }
        if (updated > 0) getUser(userId) else null
    }

    fun changePassword(userId: UserId, request: ChangePasswordRequest): Boolean = transaction {
        val storedPassword = Users.select(Users.password)
            .where { (Users.id eq userId.value) and Users.activated }
            .map { it[Users.password] }
            .singleOrNull()
        if (storedPassword == null || !HashUtils.verifyBcryptHash(
                request.oldPassword.toCharArray(),
                storedPassword.toCharArray()
            )
        ) {
            log.info("Auth check failed during password change for user {}", userId)
            return@transaction false
        }
        Credentials.checkPassword(request.newPassword)
        Users.update({ Users.id eq userId.value }) {
            it[password] = HashUtils.bcryptHash(request.newPassword)
            it[dateUpdated] = OffsetDateTime.now(ZoneOffset.UTC)
        } > 0
    }

    fun checkAuth(request: AuthRequest, twoFactor: Boolean = true): AuthOutcome = transaction {
        val (userId, storedPassword) = Users.select(Users.id, Users.password)
            .where { (Users.username eq request.username) and Users.activated }
            .map { UserId(it[Users.id]) to it[Users.password].toCharArray() }
            .singleOrNull()
            ?: return@transaction AuthOutcome(AuthResult.INVALID_CREDENTIALS)

        val twoFactorCheck = if (twoFactor) twoFactorService.validateTotp(userId, request.totp) else AuthResult.SUCCESS
        if (twoFactorCheck == AuthResult.SUCCESS) {
            if (HashUtils.verifyBcryptHash(request.password.toCharArray(), storedPassword))
                AuthOutcome(AuthResult.SUCCESS, userId)
            else
                AuthOutcome(AuthResult.INVALID_CREDENTIALS)
        } else {
            AuthOutcome(twoFactorCheck)
        }
    }

    fun findBySubject(subject: String): SubjectOwner? = transaction {
        Users.select(Users.id, Users.activated).where { Users.oidcSubject eq subject }
            .map { SubjectOwner(UserId(it[Users.id]), it[Users.activated]) }
            .singleOrNull()
    }

    // exact and case-sensitive, and never replaces an existing link
    fun linkSubjectByUsername(username: String, subject: String): UserId? = transaction {
        val userId = Users.select(Users.id)
            .where { (Users.username eq username) and Users.activated and Users.oidcSubject.isNull() }
            .map { UserId(it[Users.id]) }
            .singleOrNull() ?: return@transaction null
        val updated = Users.update({ (Users.id eq userId.value) and Users.oidcSubject.isNull() }) {
            it[oidcSubject] = subject
            it[dateUpdated] = OffsetDateTime.now(ZoneOffset.UTC)
        }
        if (updated > 0) userId else null
    }

    fun getUserActivityLog(userId: UserId, pageRequest: PageRequest = PageRequest()): Page<ActivityLogItem> = transaction {
        val sortOrder = pageRequest.direction ?: SortDirection.DESC
        val baseQuery = EntryAudit.innerJoin(Entries).select(activityLogColumns)
            .where { Entries.userId eq userId.value }
        Page.of(
            baseQuery.copy()
                .orderBy(EntryAudit.timestamp, sortOrder)
                .limit(pageRequest.size)
                .offset(max(0, (pageRequest.page - 1) * pageRequest.size))
                .map { RowMapper.toActivityLogItem(it) }, pageRequest, baseQuery.count()
        )
    }

    private companion object {
        val BCRYPT_PATTERN = Regex("""^\$2[abxy]\$\d{2}\$""")
        val JOLT_TOKEN_PATTERN = Regex("^[A-Za-z0-9_-]{1,$JOLT_TOKEN_MAX_LENGTH}$")
    }

}
