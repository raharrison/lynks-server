package lynks.util

import at.favre.lib.crypto.bcrypt.BCrypt
import lynks.comment.Comments
import lynks.common.*
import lynks.entry.ref.EntryRefs
import lynks.group.GroupType
import lynks.group.Groups
import lynks.notify.NotificationMethod
import lynks.notify.NotificationType
import lynks.notify.Notifications
import lynks.reminder.ReminderStatus
import lynks.reminder.ReminderType
import lynks.reminder.Reminders
import lynks.user.Users
import lynks.worker.WorkerSchedules
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

// owner of test data unless a test says otherwise, created before each test
val TEST_USER = UserId("test-user")

// a second user for checking that nothing crosses between users
val OTHER_USER = UserId("other-user")

fun createDummyEntry(
    id: String, title: String, content: String, type: EntryType, prop: BaseProperties? = null,
    userId: UserId = TEST_USER
) = transaction {
    val time = OffsetDateTime.now(ZoneOffset.UTC)
    Entries.insert {
        it[Entries.id] = id
        it[Entries.userId] = userId.value
        it[Entries.title] = title
        it[plainContent] = content
        it[Entries.content] = content
        it[src] = "src"
        it[Entries.type] = type
        it[dateCreated] = time
        it[dateUpdated] = time
        it[props] = prop
    }
}

fun updateDummyEntry(id: String, title: String, version: Int, thumbnailId: String? = null) = transaction {
    Entries.update({ Entries.id eq id}) {
        it[Entries.title] = title
        it[Entries.version] = version
        it[Entries.dateUpdated] = OffsetDateTime.now(ZoneOffset.UTC)
        it[Entries.thumbnailId] = thumbnailId
    }
}

fun createDummyTag(id: String, name: String, userId: UserId = TEST_USER) = transaction {
    val time = OffsetDateTime.now(ZoneOffset.UTC)
    Groups.insert {
        it[Groups.id] = id
        it[Groups.userId] = userId.value
        it[Groups.name] = name
        it[Groups.type] = GroupType.TAG
        it[dateUpdated] = time
        it[dateCreated] = time
    }
}

fun createDummyCollection(id: String, name: String, parentId: String? = null, userId: UserId = TEST_USER) = transaction {
    val time = OffsetDateTime.now(ZoneOffset.UTC)
    Groups.insert {
        it[Groups.id] = id
        it[Groups.userId] = userId.value
        it[Groups.name] = name
        it[Groups.type] = GroupType.COLLECTION
        it[Groups.parentId] = parentId
        it[dateUpdated] = time
        it[dateCreated] = time
    }
}

fun createDummyComment(id: String, entryId: String, content: String) = transaction {
    val time = OffsetDateTime.now(ZoneOffset.UTC)
    Comments.insert {
        it[Comments.id] = id
        it[Comments.entryId] = entryId
        it[plainContent] = content
        it[renderedContent] = content
        it[dateCreated] = time
        it[dateUpdated] = time
    }
}

fun createDummyReminder(id: String, entryId: String, type: ReminderType, notifyMethods: List<NotificationMethod>,
                        message: String? = null, spec: String, tz: String = ZoneId.systemDefault().id,
                        status: ReminderStatus = ReminderStatus.ACTIVE) = transaction {
    val time = OffsetDateTime.now(ZoneOffset.UTC)
    Reminders.insert {
        it[Reminders.reminderId] = id
        it[Reminders.entryId] = entryId
        it[Reminders.type] = type
        it[Reminders.notifyMethods] = notifyMethods.joinToString(",")
        it[Reminders.message] = message
        it[Reminders.spec] = spec
        it[Reminders.tz] = tz
        it[Reminders.status] = status
        it[Reminders.dateCreated] = time
        it[Reminders.dateUpdated] = time
    }
}

fun createDummyWorkerSchedule(worker: String, key: String, request: Any, lastRun: OffsetDateTime? = null) = transaction {
    WorkerSchedules.insert {
        it[WorkerSchedules.worker] = worker
        it[WorkerSchedules.key] = key
        it[WorkerSchedules.request] = JsonMapper.defaultMapper.writeValueAsString(request)
        it[WorkerSchedules.lastRun] = lastRun
    }
}

fun createDummyNotification(id: String, type: NotificationType, msg: String, eid: String?, userId: UserId = TEST_USER) =
    transaction {
    Notifications.insert {
        it[notificationId] = id
        it[Notifications.userId] = userId.value
        it[notificationType] = type
        it[message] = msg
        it[read] = false
        it[entryId] = eid
        it[dateCreated] = OffsetDateTime.now(ZoneOffset.UTC)
    }
}

// low cost, since every test creates at least one user
private val dummyPasswordHash by lazy {
    BCrypt.withDefaults().hashToString(4, DUMMY_USER_PASSWORD.toCharArray())
}

const val DUMMY_USER_PASSWORD = "password123"

fun createDummyUser(
    username: String, displayName: String? = null, digest: Boolean = false,
    id: UserId = newUserId(), activated: Boolean = true
): UserId = transaction {
    val time = OffsetDateTime.now(ZoneOffset.UTC)
    Users.insert {
        it[this.id] = id.value
        it[this.username] = username
        it[password] = dummyPasswordHash
        it[this.displayName] = displayName
        it[this.digest] = digest
        it[this.dateCreated] = time
        it[this.dateUpdated] = time
        it[this.activated] = activated
    }
    id
}

fun activateUser(username: String, activated: Boolean = true) = transaction {
    Users.update({ Users.username eq username }) {
        it[this.activated] = activated
    }
}

fun createDummyEntryRef(source: String, target: String, origin: String) = transaction {
    EntryRefs.insert {
        it[this.sourceEntryId] = source
        it[this.targetEntryId] = target
        it[this.originId] = origin
    }
}
