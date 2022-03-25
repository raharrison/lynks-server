package lynks.notify

import lynks.common.Entries
import lynks.common.EntryType
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object Notifications : Table("NOTIFICATION") {
    val notificationId = varchar("ID", 14)
    val notificationType = enumeration("TYPE", NotificationType::class)
    val message = varchar("MESSAGE", 255)
    val read = bool("READ")
    val entryId = varchar("ENTRY_ID", 14).references(Entries.id, ReferenceOption.CASCADE).nullable()
    val dateCreated = long("DATE_CREATED")
    override val primaryKey = PrimaryKey(notificationId)
}

enum class NotificationType { PROCESSED, ERROR, REMINDER, DISCUSSIONS }

enum class NotificationMethod { EMAIL, WEB, PUSHOVER }

// entry point from services to save into main table
class NewNotification private constructor(val type: NotificationType, val message: String, val entryId: String?) {
    companion object {

        fun reminder(message: String = "Reminder Elapsed", entryId: String? = null) = NewNotification(NotificationType.REMINDER, message, entryId)

        fun processed(message: String = "Processing Complete", entryId: String? = null) = NewNotification(NotificationType.PROCESSED, message, entryId)

        fun error(message: String = "An Error Occurred", entryId: String? = null) = NewNotification(NotificationType.ERROR, message, entryId)

        fun discussions(message: String = "Discussions Found", entryId: String? = null) = NewNotification(NotificationType.DISCUSSIONS, message, entryId)

    }
}

// user facing notifications with extra entry details
data class Notification(
    val id: String,
    val type: NotificationType,
    val message: String,
    val read: Boolean,
    val entryId: String? = null,
    val entryType: EntryType? = null,
    val entryTitle: String? = null,
    val dateCreated: Long
)

