package lynks.user

import lynks.common.DatabaseTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UserServiceTest: DatabaseTest() {

    private val userService = UserService()

    @Test
    fun testGetDefaultPreferences() {
        val preferences = userService.currentUserPreferences
        assertThat(preferences.email).isNull()
        assertThat(preferences.digest).isFalse()
        assertThat(preferences.tempFileCleanInterval).isEqualTo(6)
    }

    @Test
    fun testUpdatePreferencesFirst() {
        val prefs = Preferences("email.com", true)
        val updated = userService.updateUserPreferences(prefs)
        assertThat(prefs).isEqualTo(updated)
        assertThat(userService.currentUserPreferences).isEqualTo(updated)
    }

    @Test
    fun testRetrieveThenUpdate() {
        userService.currentUserPreferences
        val prefs = Preferences("email.com", true, 12)
        val updated = userService.updateUserPreferences(prefs)
        assertThat(prefs).isEqualTo(updated)
        assertThat(userService.currentUserPreferences).isEqualTo(updated)
    }

}
