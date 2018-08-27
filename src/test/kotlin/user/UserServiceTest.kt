package user

import common.DatabaseTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UserServiceTest: DatabaseTest() {

    private val userService = UserService()

    @Test
    fun testGetDefaultPreferences() {
        val preferences = userService.currentUserPreferences
        assertThat(preferences.email).isNull()
        assertThat(preferences.digest).isFalse()
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
        val prefs = Preferences("email.com", true)
        val updated = userService.updateUserPreferences(prefs)
        assertThat(prefs).isEqualTo(updated)
        assertThat(userService.currentUserPreferences).isEqualTo(updated)
    }

}