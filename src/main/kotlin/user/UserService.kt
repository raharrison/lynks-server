package user

import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import util.loggerFor

class UserService {

    private val log = loggerFor<UserService>()

    private val defaultUserId = 0
    private val defaultPreferences = Preferences()

    private var userPrefsCache: Preferences? = null

    val currentUserPreferences: Preferences
        get() {
            synchronized(this) {
                if (userPrefsCache == null) {
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
            it[email] = preferences.email
            it[digest] = preferences.digest
            it[tempFileCleanInterval] = preferences.tempFileCleanInterval
        } > 0

        if (!updated) {
            log.info("No user preferences found, inserting defaults")
            insertUserPreferences(defaultUserId, preferences)
        }
        getUserPreferences()
    }

    private fun insertUserPreferences(id: Int, preferences: Preferences): Preferences = transaction {
        UserPreferences.insert {
            it[UserPreferences.id] = id
            it[email] = preferences.email
            it[digest] = preferences.digest
            it[tempFileCleanInterval] = preferences.tempFileCleanInterval
        }
        preferences
    }

}