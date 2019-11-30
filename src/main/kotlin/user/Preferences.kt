package user

import org.jetbrains.exposed.sql.Table

object UserPreferences: Table("PREFERENCE") {
    val id = integer("ID").primaryKey()
    val email = varchar("EMAIL", 255).nullable()
    val digest = bool("DIGEST").default(false)
    val tempFileCleanInterval = long("TEMP_FILE_CLEAN_INTERVAL")
}

data class Preferences(val email: String? = null, val digest: Boolean = false, val tempFileCleanInterval: Long = 6)
