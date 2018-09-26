package user

import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class UserService {

    private val defaultUserId = 0
    private val defaultPreferences = Preferences()

    private var userPrefsCache: Preferences? = null

    val currentUserPreferences: Preferences
        get() {
            if (userPrefsCache == null) {
                synchronized(this) {
                    userPrefsCache = getUserPreferences()
                }
            }
            return userPrefsCache!!
        }

    private fun getUserPreferences(): Preferences = transaction {
        (UserPreferences.selectAll().mapNotNull {
            Preferences(it[UserPreferences.email],
                    it[UserPreferences.digest],
                    it[UserPreferences.tempFileCleanInterval])
        }.singleOrNull() ?: insertUserPreferences(defaultUserId, defaultPreferences))
                .also {
                    userPrefsCache = it
                }
    }

    fun updateUserPreferences(preferences: Preferences): Preferences = transaction {
        val updated = UserPreferences.update({ UserPreferences.id eq defaultUserId }) {
            it[UserPreferences.email] = preferences.email
            it[UserPreferences.digest] = preferences.digest
            it[UserPreferences.tempFileCleanInterval] = preferences.tempFileCleanInterval
        } > 0

        if(!updated) insertUserPreferences(defaultUserId, preferences)
        getUserPreferences()
    }

    private fun insertUserPreferences(id: Int, preferences: Preferences): Preferences = transaction {
        UserPreferences.insert {
            it[UserPreferences.id] = id
            it[UserPreferences.email] = preferences.email
            it[UserPreferences.digest] = preferences.digest
            it[UserPreferences.tempFileCleanInterval] = preferences.tempFileCleanInterval
        }
        preferences
    }

}