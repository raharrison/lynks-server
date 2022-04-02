package lynks.user

import lynks.common.DatabaseTest
import lynks.common.exception.InvalidModelException
import lynks.util.createDummyUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UserServiceTest : DatabaseTest() {

    private val userService = UserService()

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
    }

    @Test
    fun testGetUserNotFound() {
        assertThat(userService.getUser("notFound")).isNull()
    }

    @Test
    fun testRegisterUser() {
        val registered = userService.register(AuthRequest("user2", "pass"))
        assertThat(registered.username).isEqualTo("user2")
        assertThat(registered.email).isNull()
        assertThat(registered.displayName).isNull()
        assertThat(registered.digest).isFalse()
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
        val updated = userService.updateUser(User("user1", "updated@mail.com", "Bill Smith"))
        assertThat(updated).isNotNull()
        assertThat(updated?.email).isEqualTo("updated@mail.com")
        assertThat(updated?.displayName).isEqualTo("Bill Smith")
        assertThat(userService.getUser("user1")).isEqualTo(updated)
    }

    @Test
    fun testUpdateUserNotFound() {
        val updated = userService.updateUser(User("notfound", "updated@mail.com"))
        assertThat(updated).isNull()
    }

    @Test
    fun testCheckAuthSuccess() {
        val pass = "pass123"
        val user = userService.register(AuthRequest("user2", pass))
        assertThat(userService.checkAuth(AuthRequest(user.username, pass))).isTrue()
    }

    @Test
    fun testCheckAuthFailure() {
        val user = userService.register(AuthRequest("user2", "pass123"))
        // invalid username
        assertThat(userService.checkAuth(AuthRequest("invalid", "pass123"))).isFalse()
        // invalid password
        assertThat(userService.checkAuth(AuthRequest(user.username, "invalid"))).isFalse()
    }

    @Test
    fun testChangePasswordSuccess() {
        val originalPass = "pass123"
        val newPass = "pass456"
        val user = userService.register(AuthRequest("user2", originalPass))
        assertThat(userService.checkAuth(AuthRequest(user.username, originalPass))).isTrue()
        val changed = userService.changePassword(ChangePasswordRequest(user.username, originalPass, newPass))
        assertThat(changed).isTrue()
        assertThat(userService.checkAuth(AuthRequest(user.username, originalPass))).isFalse()
        assertThat(userService.checkAuth(AuthRequest(user.username, newPass))).isTrue()
    }

    @Test
    fun testChangePasswordBadAuth() {
        val originalPass = "pass123"
        val newPass = "pass456"
        val user = userService.register(AuthRequest("user2", originalPass))
        // invalid username
        assertThat(userService.changePassword(ChangePasswordRequest("invalid", originalPass, newPass))).isFalse()
        // invalid old password
        assertThat(userService.changePassword(ChangePasswordRequest(user.username, "invalid", newPass))).isFalse()
    }

    @Test
    fun testGetDigestEnabledEmails() {
        createDummyUser("user2", "user2@mail.com", "Bill Smith", digest = true)
        createDummyUser("user3", "user3@mail.com", "Bert Smith", digest = true)
        val enabledEmails = userService.getDigestEnabledEmails()
        assertThat(enabledEmails).containsExactlyInAnyOrder("user2@mail.com", "user3@mail.com")
    }


}
