package user

import org.jetbrains.exposed.sql.Table

object UserPreferences: Table("Preference") {
    val id = integer("id").primaryKey()
    val email = varchar("email", 255).nullable()
    val digest = bool("digest").default(false)
    val tempFileCleanInterval = long("tempFileCleanInterval")
}

data class Preferences(val email: String? = null, val digest: Boolean = false, val tempFileCleanInterval: Long = 6)
