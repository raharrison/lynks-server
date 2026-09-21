package lynks.user

import lynks.common.Entries
import lynks.common.EntryAudit
import lynks.common.RowMapper
import lynks.common.exception.InvalidModelException
import lynks.common.page.Page
import lynks.common.page.PageRequest
import lynks.common.page.SortDirection
import lynks.util.HashUtils
import lynks.util.loggerFor
import lynks.util.orderBy
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
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
    private val userColumns = Users.columns - Users.password
    private val activityLogColumns = EntryAudit.columns + listOf(Entries.type, Entries.title)

    fun getUser(username: String): User? = transaction {
        Users.select(userColumns).where { Users.username eq username and Users.activated }.map {
            User(
                it[Users.username],
                it[Users.displayName],
                it[Users.digest],
                it[Users.dateCreated].toInstant(),
                it[Users.dateUpdated].toInstant()
            )
        }.singleOrNull()
    }

    fun register(request: AuthRequest): String = transaction {
        if (Users.selectAll().where { Users.username eq request.username }.count() > 0) {
            throw InvalidModelException("User with that name already exists")
        }
        val currentTime = OffsetDateTime.now(ZoneOffset.UTC)
        Users.insert {
            it[username] = request.username
            it[password] = HashUtils.bcryptHash(request.password)
            it[dateCreated] = currentTime
            it[dateUpdated] = currentTime
            it[activated] = false
        }
        log.info("Successfully registered new user {}", request.username)
        request.username
    }

    fun activateUser(username: String): Int = transaction {
        Users.update({ Users.username eq username }) {
            it[activated] = true
            it[dateUpdated] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    fun updateUser(userUpdate: UserUpdateRequest): User? = transaction {
        val updated = Users.update({ Users.username eq userUpdate.username and Users.activated }) {
            it[displayName] = userUpdate.displayName
            it[digest] = userUpdate.digest
            it[dateUpdated] = OffsetDateTime.now(ZoneOffset.UTC)
        }
        if (updated > 0) getUser(userUpdate.username) else null
    }

    fun changePassword(request: ChangePasswordRequest): Boolean = transaction {
        if (checkAuth(AuthRequest(request.username, request.oldPassword), false) == AuthResult.SUCCESS) {
            return@transaction Users.update({ Users.username eq request.username and Users.activated }) {
                it[password] = HashUtils.bcryptHash(request.newPassword)
                it[dateUpdated] = OffsetDateTime.now(ZoneOffset.UTC)
            } > 0
        }
        log.info("Auth check failed during password change for user {}", request.username)
        false
    }

    fun checkAuth(request: AuthRequest, twoFactor: Boolean = true): AuthResult = transaction {
        val storedPassword = Users.select(Users.password)
            .where { Users.username eq request.username and Users.activated }
            .map { it[Users.password].toCharArray() }.singleOrNull()
            ?: return@transaction AuthResult.INVALID_CREDENTIALS

        val twoFactorCheck = if(twoFactor) twoFactorService.validateTotp(request.username, request.totp) else AuthResult.SUCCESS
        if (twoFactorCheck == AuthResult.SUCCESS) {
            if (HashUtils.verifyBcryptHash(request.password.toCharArray(), storedPassword))
                AuthResult.SUCCESS
            else
                AuthResult.INVALID_CREDENTIALS
        } else {
            twoFactorCheck
        }
    }

    fun isDigestEnabled(): Boolean = transaction {
        Users.selectAll().where { Users.digest and Users.activated }.empty().not()
    }

    fun getUserActivityLog(pageRequest: PageRequest = PageRequest()): Page<ActivityLogItem> = transaction {
        val sortOrder = pageRequest.direction ?: SortDirection.DESC
        val baseQuery = EntryAudit.leftJoin(Entries).select(activityLogColumns)
        Page.of(
            baseQuery.copy()
                .orderBy(EntryAudit.timestamp, sortOrder)
                .limit(pageRequest.size)
                .offset(max(0, (pageRequest.page - 1) * pageRequest.size))
                .map { RowMapper.toActivityLogItem(it) }, pageRequest, baseQuery.count()
        )
    }

}
