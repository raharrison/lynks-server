package lynks.user

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import lynks.common.*
import lynks.common.exception.InvalidModelException
import lynks.common.page.PageRequest
import lynks.entry.EntryAuditService
import lynks.util.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UserServiceTest : DatabaseTest() {

    private val twoFactorService = mockk<TwoFactorService>()
    private val entryAuditService = EntryAuditService()
    private val userService = UserService(twoFactorService)
    private val user1 = UserId("user1-id")

    @BeforeEach
    fun setup() {
        createDummyUser("user1", "Bob Smith", id = user1)
    }

    private fun registerAndActivate(username: String, password: String): UserId {
        userService.register(AuthRequest(username, password))
        activateUser(username)
        return userService.getPrincipal(username)!!.id
    }

    @Test
    fun testGetUser() {
        val user = userService.getUser(user1)
        assertThat(user).isNotNull()
        assertThat(user?.id).isEqualTo(user1)
        assertThat(user?.username).isEqualTo("user1")
        assertThat(user?.displayName).isEqualTo("Bob Smith")
        assertThat(user?.digest).isFalse()
        assertThat(user?.dateCreated).isEqualTo(user?.dateUpdated)
    }

    @Test
    fun testGetUserNotFound() {
        assertThat(userService.getUser(UserId("notFound"))).isNull()
    }

    @Test
    fun testGetUserNotActivated() {
        activateUser("user1", false)
        assertThat(userService.getUser(user1)).isNull()
    }

    @Test
    fun testGetPrincipal() {
        assertThat(userService.getPrincipal(user1)).isEqualTo(UserPrincipal(user1, "user1"))
        assertThat(userService.getPrincipal("user1")).isEqualTo(UserPrincipal(user1, "user1"))
        assertThat(userService.getPrincipal(UserId("invalid"))).isNull()
        assertThat(userService.getPrincipal("invalid")).isNull()
        activateUser("user1", false)
        assertThat(userService.getPrincipal(user1)).isNull()
        assertThat(userService.getPrincipal("user1")).isNull()
    }

    @Test
    fun testGetActiveUsers() {
        createDummyUser("inactive", activated = false)
        assertThat(userService.getActiveUsers()).extracting<String> { it.username }
            .containsExactlyInAnyOrder("test-user", "user1")
    }

    @Test
    fun testRegisterUser() {
        val registeredUsername = userService.register(AuthRequest("user2", "password1"))
        assertThat(registeredUsername).isEqualTo("user2")
        // inactive until activated
        assertThat(userService.getPrincipal("user2")).isNull()
        activateUser(registeredUsername)
        val registered = userService.getUser(userService.getPrincipal("user2")!!.id)
        assertThat(registered).isNotNull()
        assertThat(registered?.username).isEqualTo("user2")
        assertThat(registered?.displayName).isNull()
        assertThat(registered?.digest).isFalse()
    }

    @Test
    fun testRegisterUserAlreadyExists() {
        assertThrows<InvalidModelException> {
            userService.register(AuthRequest("user1", "password1"))
        }
    }

    @Test
    fun testRegisterInvalidUsername() {
        listOf("ab", "a".repeat(USERNAME_MAX_LENGTH + 1), "with space", "slash/name", "semi;colon", "").forEach {
            assertThrows<InvalidModelException> { userService.register(AuthRequest(it, "password1")) }
        }
        assertThat(userService.getActiveUsers()).hasSize(2)
    }

    @Test
    fun testRegisterInvalidPassword() {
        assertThrows<InvalidModelException> { userService.register(AuthRequest("user2", "short")) }
        assertThrows<InvalidModelException> { userService.register(AuthRequest("user2", "a".repeat(73))) }
        // multi-byte characters count against the bcrypt limit in bytes
        assertThrows<InvalidModelException> { userService.register(AuthRequest("user2", "é".repeat(37))) }
        assertThat(userService.getPrincipal("user2")).isNull()
    }

    @Test
    fun testUpdateUser() {
        val before = userService.getUser(user1)
        assertThat(before?.displayName).isEqualTo("Bob Smith")
        Thread.sleep(10)
        val updated = userService.updateUser(user1, UserUpdateRequest("Bill Smith"))
        assertThat(updated).isNotNull()
        assertThat(updated?.displayName).isEqualTo("Bill Smith")
        assertThat(updated?.dateCreated).isEqualTo(before?.dateCreated)
        assertThat(updated?.dateUpdated).isNotEqualTo(before?.dateUpdated)
        assertThat(userService.getUser(user1)).isEqualTo(updated)
        assertThat(userService.getUser(TEST_USER)?.displayName).isNull()
    }

    @Test
    fun testUpdateUserNotFound() {
        val updated = userService.updateUser(UserId("notfound"), UserUpdateRequest("Bill Smith"))
        assertThat(updated).isNull()
    }

    @Test
    fun testCheckAuthSuccess() {
        val pass = "password123"
        val userId = registerAndActivate("user2", pass)
        every { twoFactorService.validateTotp(userId, "totp") } returns AuthResult.SUCCESS
        assertThat(userService.checkAuth(AuthRequest("user2", pass, "totp")))
            .isEqualTo(AuthOutcome(AuthResult.SUCCESS, userId))
        verify(exactly = 1) { twoFactorService.validateTotp(userId, "totp") }
    }

    @Test
    fun testCheckAuthFailureUserNotActivated() {
        userService.register(AuthRequest("user2", "password123"))
        assertThat(userService.checkAuth(AuthRequest("user2", "password123")).result).isEqualTo(AuthResult.INVALID_CREDENTIALS)
        verify(exactly = 0) { twoFactorService.validateTotp(any(), any())  }
    }

    @Test
    fun testCheckAuthFailureInvalidUserNameOrPassword() {
        val userId = registerAndActivate("user2", "password123")
        every { twoFactorService.validateTotp(userId, null) } returns AuthResult.SUCCESS
        // invalid username
        assertThat(userService.checkAuth(AuthRequest("invalid", "password123")))
            .isEqualTo(AuthOutcome(AuthResult.INVALID_CREDENTIALS))
        // invalid password
        assertThat(userService.checkAuth(AuthRequest("user2", "invalid")))
            .isEqualTo(AuthOutcome(AuthResult.INVALID_CREDENTIALS))
        verify(exactly = 1) { twoFactorService.validateTotp(userId, null) }
    }

    @Test
    fun testCheckAuthFailureInvalidTotp() {
        val pass = "password123"
        val userId = registerAndActivate("user2", pass)
        every { twoFactorService.validateTotp(userId, null) } returns AuthResult.TOTP_REQUIRED
        assertThat(userService.checkAuth(AuthRequest("user2", pass))).isEqualTo(AuthOutcome(AuthResult.TOTP_REQUIRED))
        verify(exactly = 1) { twoFactorService.validateTotp(userId, null) }
    }

    @Test
    fun testDeactivatedUserCannotSignIn() {
        every { twoFactorService.validateTotp(user1, null) } returns AuthResult.SUCCESS
        assertThat(userService.checkAuth(AuthRequest("user1", DUMMY_USER_PASSWORD)).result).isEqualTo(AuthResult.SUCCESS)
        activateUser("user1", false)
        assertThat(
            userService.checkAuth(
                AuthRequest(
                    "user1",
                    DUMMY_USER_PASSWORD
                )
            ).result
        ).isEqualTo(AuthResult.INVALID_CREDENTIALS)
    }

    @Test
    fun testChangePasswordSuccess() {
        val originalPass = "password123"
        val newPass = "password456"
        val userId = registerAndActivate("user2", originalPass)
        every { twoFactorService.validateTotp(userId, any()) } returns AuthResult.SUCCESS
        assertThat(userService.checkAuth(AuthRequest("user2", originalPass)).result).isEqualTo(AuthResult.SUCCESS)
        val changed = userService.changePassword(userId, ChangePasswordRequest(originalPass, newPass))
        assertThat(changed).isTrue()
        assertThat(userService.checkAuth(AuthRequest("user2", originalPass)).result).isEqualTo(AuthResult.INVALID_CREDENTIALS)
        assertThat(userService.checkAuth(AuthRequest("user2", newPass)).result).isEqualTo(AuthResult.SUCCESS)
    }

    @Test
    fun testChangePasswordBadAuth() {
        val originalPass = "original-pass"
        val newPass = "password456"
        val userId = registerAndActivate("user2", originalPass)
        // unknown user
        assertThat(userService.changePassword(UserId("invalid"), ChangePasswordRequest(originalPass, newPass))).isFalse()
        // invalid old password
        assertThat(userService.changePassword(userId, ChangePasswordRequest("invalid", newPass))).isFalse()
        // another user's old password
        assertThat(userService.changePassword(user1, ChangePasswordRequest(originalPass, newPass))).isFalse()
    }

    @Test
    fun testChangePasswordInvalidNewPassword() {
        val userId = registerAndActivate("user2", "password123")
        assertThrows<InvalidModelException> {
            userService.changePassword(userId, ChangePasswordRequest("password123", "short"))
        }
    }

    @Test
    fun testJoltToken() {
        assertThat(userService.getJoltToken(user1)).isNull()
        assertThat(userService.getUser(user1)?.joltConfigured).isFalse()

        val updated = userService.updateJoltToken(user1, " jlt_live_abc-123 ")
        assertThat(updated?.joltConfigured).isTrue()
        assertThat(userService.getJoltToken(user1)).isEqualTo("jlt_live_abc-123")
        assertThat(userService.getJoltToken(TEST_USER)).isNull()

        userService.updateJoltToken(user1, "")
        assertThat(userService.getJoltToken(user1)).isNull()
        assertThat(userService.getUser(user1)?.joltConfigured).isFalse()
    }

    @Test
    fun testJoltTokenMustBeUrlSafe() {
        listOf("../admin", "with space", "a?b", "a".repeat(JOLT_TOKEN_MAX_LENGTH + 1)).forEach {
            assertThrows<InvalidModelException> { userService.updateJoltToken(user1, it) }
        }
        assertThat(userService.getJoltToken(user1)).isNull()
        assertThat(userService.updateJoltToken(UserId("invalid"), "token")).isNull()
    }

    @Test
    fun testEnsureDefaultUserWhenAuthDisabled() {
        val username = Environment.auth.defaultUserName
        val password = Environment.auth.defaultUserPassword!!
        assertThat(Environment.auth.enabled).isFalse()
        assertThat(userService.getPrincipal(username)).isNull()

        userService.ensureDefaultUser()
        val principal = userService.getPrincipal(username)
        assertThat(principal).isNotNull()
        every { twoFactorService.validateTotp(principal!!.id, null) } returns AuthResult.SUCCESS
        assertThat(userService.checkAuth(AuthRequest(username, password))).isEqualTo(
            AuthOutcome(
                AuthResult.SUCCESS,
                principal!!.id
            )
        )

        // idempotent, and never resurrects a deactivated default user
        userService.ensureDefaultUser()
        activateUser(username, false)
        userService.ensureDefaultUser()
        assertThat(userService.getPrincipal(username)).isNull()
        assertThat(userService.getActiveUsers()).extracting<String> { it.username }
            .containsExactlyInAnyOrder("test-user", "user1")
    }

    @Test
    fun testGetActivityLog() {
        createDummyEntry("e1", "note1", "note content", EntryType.NOTE)
        entryAuditService.acceptAuditEvent(EntryId("e1"), "source", "message")
        Thread.sleep(10)
        entryAuditService.acceptAuditEvent(EntryId("e1"), "source2", "message2")

        val activityLog = userService.getUserActivityLog(TEST_USER)
        assertThat(activityLog.total).isEqualTo(2)
        assertThat(activityLog.page).isOne()
        assertThat(activityLog.content).hasSize(2)
        assertThat(activityLog.content).extracting<EntryId> { it.entryId }
            .containsOnly(EntryId("e1"))
        assertThat(activityLog.content).extracting("details").doesNotContainNull()
        assertThat(activityLog.content).extracting("entryType").containsOnly(EntryType.NOTE)
        assertThat(activityLog.content).extracting("details").containsOnly("message2", "message")

        val activityLogPaged = userService.getUserActivityLog(TEST_USER, PageRequest(page = 2, size = 1))
        assertThat(activityLogPaged.total).isEqualTo(2)
        assertThat(activityLogPaged.page).isEqualTo(2)
        assertThat(activityLogPaged.size).isOne()
        assertThat(activityLogPaged.content).hasSize(1)
        assertThat(activityLogPaged.content).extracting<EntryId> { it.entryId }
            .containsOnly(EntryId("e1"))
        assertThat(activityLogPaged.content).extracting("details").doesNotContainNull()
        assertThat(activityLogPaged.content).extracting("entryType").containsOnly(EntryType.NOTE)
        assertThat(activityLogPaged.content).extracting("entryTitle").containsOnly("note1")
        assertThat(activityLogPaged.content).extracting("details").containsOnly("message")
    }

    @Test
    fun testActivityLogOnlyIncludesOwnEntries() {
        createDummyEntry("e1", "note1", "note content", EntryType.NOTE)
        createDummyEntry("e2", "note2", "note content", EntryType.NOTE, userId = user1)
        entryAuditService.acceptAuditEvent(EntryId("e1"), "source", "mine")
        entryAuditService.acceptAuditEvent(EntryId("e2"), "source", "theirs")

        assertThat(userService.getUserActivityLog(TEST_USER).content).extracting("details").containsExactly("mine")
        assertThat(userService.getUserActivityLog(user1).content).extracting("details").containsExactly("theirs")
    }

}
