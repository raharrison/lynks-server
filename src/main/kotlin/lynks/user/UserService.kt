package lynks.user

import lynks.common.exception.InvalidModelException
import lynks.util.HashUtils
import lynks.util.loggerFor
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class UserService {

    private val log = loggerFor<UserService>()

    fun getUser(username: String): User? = transaction {
        Users.select { Users.username eq username }.map {
            User(it[Users.username],
                it[Users.email],
                it[Users.displayName],
                it[Users.digest],
                it[Users.dateCreated],
                it[Users.dateUpdated]
            )
        }.singleOrNull()
    }

    fun register(request: AuthRequest): User = transaction {
        val existing = getUser(request.username)
        if (existing != null) {
            throw InvalidModelException("User with that name already exists")
        }
        val currentTime = System.currentTimeMillis()
        Users.insert {
            it[username] = request.username
            it[password] = HashUtils.bcryptHash(request.password)
            it[dateCreated] = currentTime
            it[dateUpdated] = currentTime
        }
        log.info("Successfully registered new user {}", request.username)
        getUser(request.username)!!
    }

    fun updateUser(userUpdate: UserUpdateRequest): User? = transaction {
        val updated = Users.update({ Users.username eq userUpdate.username }) {
            it[email] = userUpdate.email
            it[displayName] = userUpdate.displayName
            it[digest] = userUpdate.digest
            it[dateUpdated] = System.currentTimeMillis()
        }
        if (updated > 0) getUser(userUpdate.username) else null
    }

    fun changePassword(request: ChangePasswordRequest): Boolean = transaction {
        if (checkAuth(AuthRequest(request.username, request.oldPassword))) {
            return@transaction Users.update({ Users.username eq request.username }) {
                it[password] = HashUtils.bcryptHash(request.newPassword)
                it[dateUpdated] = System.currentTimeMillis()
            } > 0
        }
        log.info("Auth check failed during password change for user {}", request.username)
        false
    }

    fun checkAuth(request: AuthRequest): Boolean = transaction {
        val storedPassword = Users.slice(Users.password)
            .select { Users.username eq request.username }
            .map { it[Users.password].toCharArray() }.singleOrNull()
            ?: return@transaction false
        HashUtils.verifyBcryptHash(request.password.toCharArray(), storedPassword)
    }

    fun getDigestEnabledEmails(): Set<String> = transaction {
        Users.slice(Users.email)
            .select { Users.digest eq true and Users.email.isNotNull() }
            .mapNotNull { it[Users.email] }.toSet()
    }

}
