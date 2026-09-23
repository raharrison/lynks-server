package lynks.notify

import lynks.common.*
import lynks.user.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.Instant

object Notifications : Table("notifications") {
    val notificationId = varchar("id", UID_LENGTH)
    val userId = varchar("user_id", UID_LENGTH).references(Users.id, ReferenceOption.CASCADE)
    val notificationType = enumerationByName<NotificationType>("type", 30)
    val message = varchar("message", 255)
    val read = bool("read")
    val entryId = varchar("entry_id", UID_LENGTH).references(Entries.id, ReferenceOption.CASCADE).nullable().index()
    val dateCreated = timestampWithTimeZone("date_created")
    override val primaryKey = PrimaryKey(notificationId)

    init {
        index(false, userId, dateCreated)
    }
}

enum class NotificationType { PROCESSED, ERROR, REMINDER, DISCUSSIONS, DIGEST }

// PUSH is the in-app notification list, which the UI polls; JOLT also hands off to jolt's inbound channel
enum class NotificationMethod { PUSH, JOLT }

// entry point from services to save into main table
class NewNotification private constructor(val type: NotificationType, val message: String, val entryId: EntryId?) {
    companion object {

        fun reminder(message: String = "Reminder Elapsed", entryId: EntryId? = null) = NewNotification(NotificationType.REMINDER, message, entryId)

        fun processed(message: String = "Processing Complete", entryId: EntryId? = null) = NewNotification(NotificationType.PROCESSED, message, entryId)

        fun error(message: String = "An Error Occurred", entryId: EntryId? = null) = NewNotification(NotificationType.ERROR, message, entryId)

        fun discussions(message: String = "Discussions Found", entryId: EntryId? = null) = NewNotification(NotificationType.DISCUSSIONS, message, entryId)

        fun digest(message: String = "Digest Regenerated") = NewNotification(NotificationType.DIGEST, message, null)

    }
}

// user facing notifications with extra entry details
data class Notification(
    override val id: NotificationId,
    val type: NotificationType,
    val message: String,
    val read: Boolean,
    val entryId: EntryId? = null,
    val entryType: EntryType? = null,
    val entryTitle: String? = null,
    val dateCreated: Instant
) : TypedIdEntity<NotificationId>

