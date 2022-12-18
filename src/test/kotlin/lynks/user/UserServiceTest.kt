package lynks.user

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import lynks.common.DatabaseTest
import lynks.common.EntryType
import lynks.common.exception.InvalidModelException
import lynks.common.page.PageRequest
import lynks.entry.EntryAuditService
import lynks.util.createDummyEntry
import lynks.util.createDummyUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UserServiceTest : DatabaseTest() {

    private val twoFactorService = mockk<TwoFactorService>()
    private val entryAuditService = EntryAuditService()
    private val userService = UserService(twoFactorService)

    @BeforeEach
    fun setup() {
        createDummyUser("user1", "user1@mail.com", "Bob Smith")
    }

    @Test
    fun testGetUser() {
        val user = userService.getUser("user1")
        assertThat(user).isNotNull()
        assertThat(user?.username).isEqualTo("user1")
        assertThat(user?.email).isEqualTo("user1@mail.com")
        assertThat(user?.displayName).isEqualTo("Bob Smith")
        assertThat(user?.digest).isFalse()
        assertThat(user?.dateCreated).isEqualTo(user?.dateUpdated)
    }

    @Test
    fun testGetUserNotFound() {
        assertThat(userService.getUser("notFound")).isNull()
    }

    @Test
    fun testRegisterUser() {
        val registeredUsername = userService.register(AuthRequest("user2", "pass"))
        assertThat(registeredUsername).isEqualTo("user2")
        userService.activateUser(registeredUsername)
        val registered = userService.getUser(registeredUsername)
        assertThat(registered).isNotNull()
        assertThat(registered?.username).isEqualTo("user2")
        assertThat(registered?.email).isNull()
        assertThat(registered?.displayName).isNull()
        assertThat(registered?.digest).isFalse()
        // different after activation
        assertThat(registered?.dateCreated).isNotEqualTo(registered?.dateUpdated)
    }

    @Test
    fun testRegisterUserAlreadyExists() {
        assertThrows<InvalidModelException> {
            userService.register(AuthRequest("user1", "pass"))
        }
    }

    @Test
    fun testUpdateUser() {
        val before = userService.getUser("user1")
        assertThat(before?.email).isEqualTo("user1@mail.com")
        assertThat(before?.displayName).isEqualTo("Bob Smith")
        Thread.sleep(10)
        val updated = userService.updateUser(UserUpdateRequest("user1", "updated@mail.com", "Bill Smith"))
        assertThat(updated).isNotNull()
        assertThat(updated?.email).isEqualTo("updated@mail.com")
        assertThat(updated?.displayName).isEqualTo("Bill Smith")
        assertThat(updated?.dateCreated).isEqualTo(before?.dateCreated)
        assertThat(updated?.dateUpdated).isNotEqualTo(before?.dateUpdated)
        assertThat(userService.getUser("user1")).isEqualTo(updated)
    }

    @Test
    fun testUpdateUserNotFound() {
        val updated = userService.updateUser(UserUpdateRequest("notfound", "updated@mail.com"))
        assertThat(updated).isNull()
    }

    @Test
    fun testCheckAuthSuccess() {
        val pass = "pass123"
        every { twoFactorService.validateTotp("user2", "totp") } returns AuthResult.SUCCESS
        val username = userService.register(AuthRequest("user2", pass))
        userService.activateUser(username)
        assertThat(userService.checkAuth(AuthRequest(username, pass, "totp"))).isEqualTo(AuthResult.SUCCESS)
        verify(exactly = 1) { twoFactorService.validateTotp(username, "totp") }
    }

    @Test
    fun testCheckAuthFailureUserNotActivated() {
        val username = userService.register(AuthRequest("user2", "pass123"))
        assertThat(userService.checkAuth(AuthRequest(username, "pass123"))).isEqualTo(AuthResult.INVALID_CREDENTIALS)
        verify(exactly = 0) { twoFactorService.validateTotp(any(), any())  }
    }

    @Test
    fun testCheckAuthFailureInvalidUserNameOrPassword() {
        every { twoFactorService.validateTotp("user2", null) } returns AuthResult.SUCCESS
        val username = userService.register(AuthRequest("user2", "pass123"))
        userService.activateUser(username)
        // invalid username
        assertThat(userService.checkAuth(AuthRequest("invalid", "pass123"))).isEqualTo(AuthResult.INVALID_CREDENTIALS)
        // invalid password
        assertThat(userService.checkAuth(AuthRequest(username, "invalid"))).isEqualTo(AuthResult.INVALID_CREDENTIALS)
        verify(exactly = 1) { twoFactorService.validateTotp("user2", null)  }
    }

    @Test
    fun testCheckAuthFailureInvalidTotp() {
        val pass = "pass123"
        every { twoFactorService.validateTotp("user2", null) } returns AuthResult.TOTP_REQUIRED
        val username = userService.register(AuthRequest("user2", pass))
        userService.activateUser(username)
        assertThat(userService.checkAuth(AuthRequest(username, pass))).isEqualTo(AuthResult.TOTP_REQUIRED)
        verify(exactly = 1) { twoFactorService.validateTotp(username, null) }
    }

    @Test
    fun testActivateUser() {
        val pass = "pass123"
        every { twoFactorService.validateTotp("user2", any()) } returns AuthResult.SUCCESS
        val username = userService.register(AuthRequest("user2", pass))
        assertThat(userService.checkAuth(AuthRequest(username, pass))).isEqualTo(AuthResult.INVALID_CREDENTIALS)
        userService.activateUser(username)
        assertThat(userService.checkAuth(AuthRequest(username, pass))).isEqualTo(AuthResult.SUCCESS)
    }

    @Test
    fun testChangePasswordSuccess() {
        val originalPass = "pass123"
        val newPass = "pass456"
        every { twoFactorService.validateTotp("user2", any()) } returns AuthResult.SUCCESS
        val username = userService.register(AuthRequest("user2", originalPass))
        userService.activateUser(username)
        assertThat(userService.checkAuth(AuthRequest(username, originalPass))).isEqualTo(AuthResult.SUCCESS)
        val changed = userService.changePassword(ChangePasswordRequest(username, originalPass, newPass))
        assertThat(changed).isTrue()
        assertThat(userService.checkAuth(AuthRequest(username, originalPass))).isEqualTo(AuthResult.INVALID_CREDENTIALS)
        assertThat(userService.checkAuth(AuthRequest(username, newPass))).isEqualTo(AuthResult.SUCCESS)
    }

    @Test
    fun testChangePasswordBadAuth() {
        val originalPass = "pass123"
        val newPass = "pass456"
        every { twoFactorService.validateTotp(any(), any()) } returns AuthResult.SUCCESS
        val username = userService.register(AuthRequest("user2", originalPass))
        userService.activateUser(username)
        // invalid username
        assertThat(userService.changePassword(ChangePasswordRequest("invalid", originalPass, newPass))).isFalse()
        // invalid old password
        assertThat(userService.changePassword(ChangePasswordRequest(username, "invalid", newPass))).isFalse()
    }

    @Test
    fun testGetDigestEnabledEmails() {
        createDummyUser("user2", "user2@mail.com", "Bill Smith", digest = true)
        createDummyUser("user3", "user3@mail.com", "Bert Smith", digest = true)
        val enabledEmails = userService.getDigestEnabledEmails()
        assertThat(enabledEmails).containsExactlyInAnyOrder("user2@mail.com", "user3@mail.com")
    }

    @Test
    fun testGetActivityLog() {
        createDummyEntry("e1", "note1", "note content", EntryType.NOTE)
        entryAuditService.acceptAuditEvent("e1", "source", "message")
        Thread.sleep(10)
        entryAuditService.acceptAuditEvent("e1", "source2", "message2")

        val activityLog = userService.getUserActivityLog()
        assertThat(activityLog.total).isEqualTo(2)
        assertThat(activityLog.page).isOne()
        assertThat(activityLog.content).hasSize(2)
        assertThat(activityLog.content).extracting("entryId").containsOnly("e1")
        assertThat(activityLog.content).extracting("details").doesNotContainNull()
        assertThat(activityLog.content).extracting("entryType").containsOnly(EntryType.NOTE)
        assertThat(activityLog.content).extracting("details").containsOnly("message2", "message")

        val activityLogPaged = userService.getUserActivityLog(PageRequest(page = 2, size = 1))
        assertThat(activityLogPaged.total).isEqualTo(2)
        assertThat(activityLogPaged.page).isEqualTo(2)
        assertThat(activityLogPaged.size).isOne()
        assertThat(activityLogPaged.content).hasSize(1)
        assertThat(activityLogPaged.content).extracting("entryId").containsOnly("e1")
        assertThat(activityLogPaged.content).extracting("details").doesNotContainNull()
        assertThat(activityLogPaged.content).extracting("entryType").containsOnly(EntryType.NOTE)
        assertThat(activityLogPaged.content).extracting("entryTitle").containsOnly("note1")
        assertThat(activityLogPaged.content).extracting("details").containsOnly("message")
    }


}
