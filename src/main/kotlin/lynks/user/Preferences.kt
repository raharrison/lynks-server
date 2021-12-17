package lynks.user

import org.jetbrains.exposed.sql.Table

object UserPreferences: Table("PREFERENCE") {
    val id = integer("ID")
    val email = varchar("EMAIL", 255).nullable()
    val digest = bool("DIGEST").default(false)
    val tempFileCleanInterval = long("TEMP_FILE_CLEAN_INTERVAL")
    override val primaryKey = PrimaryKey(id)
}

data class Preferences(val email: String? = null, val digest: Boolean = false, val tempFileCleanInterval: Long = 6)
