package lynks.util

import lynks.comment.Comments
import lynks.common.BaseProperties
import lynks.common.Entries
import lynks.common.EntryType
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
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.ZoneId

fun createDummyEntry(id: String, title: String, content: String, type: EntryType, prop: BaseProperties? = null) = transaction {
    val time = System.currentTimeMillis()
    Entries.insert {
        it[Entries.id] = id
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
        it[Entries.dateUpdated] = System.currentTimeMillis()
        it[Entries.thumbnailId] = thumbnailId
    }
}

fun createDummyTag(id: String, name: String) = transaction{
    val time = System.currentTimeMillis()
    Groups.insert {
        it[Groups.id] = id
        it[Groups.name] = name
        it[Groups.type] = GroupType.TAG
        it[dateUpdated] = time
        it[dateCreated] = time
    }
}

fun createDummyCollection(id: String, name: String, parentId: String?=null) = transaction {
    val time = System.currentTimeMillis()
    Groups.insert {
        it[Groups.id] = id
        it[Groups.name] = name
        it[Groups.type] = GroupType.COLLECTION
        it[Groups.parentId] = parentId
        it[dateUpdated] = time
        it[dateCreated] = time
    }
}

fun createDummyComment(id: String, entryId: String, content: String) = transaction {
    val time = System.currentTimeMillis()
    Comments.insert {
        it[Comments.id] = id
        it[Comments.entryId] = entryId
        it[plainText] = content
        it[markdownText] = content
        it[dateCreated] = time
        it[dateUpdated] = time
    }
}

fun createDummyReminder(id: String, entryId: String, type: ReminderType, notifyMethods: List<NotificationMethod>,
                        message: String? = null, spec: String, tz: String = ZoneId.systemDefault().id,
                        status: ReminderStatus = ReminderStatus.ACTIVE) = transaction {
    val time = System.currentTimeMillis()
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

fun createDummyWorkerSchedule(worker: String, key: String, request: Any, lastRun: Long? = null) = transaction {
    WorkerSchedules.insert {
        it[WorkerSchedules.worker] = worker
        it[WorkerSchedules.key] = key
        it[WorkerSchedules.request] = JsonMapper.defaultMapper.writeValueAsString(request)
        it[WorkerSchedules.lastRun] = lastRun
    }
}

fun createDummyNotification(id: String, type: NotificationType, msg: String, eid: String?) = transaction {
    Notifications.insert {
        it[notificationId] = id
        it[notificationType] = type
        it[message] = msg
        it[read] = false
        it[entryId] = eid
        it[dateCreated] = System.currentTimeMillis()
    }
}

fun createDummyUser(username: String, email: String? = null, displayName: String? = null, digest: Boolean = false) = transaction {
    val time = System.currentTimeMillis()
    Users.insert {
        it[this.username] = username
        it[password] = "pass"
        it[this.email] = email
        it[this.displayName] = displayName
        it[this.digest] = digest
        it[this.dateCreated] = time
        it[this.dateUpdated] = time
        it[this.activated] = true
    }
}

fun activateUser(username: String) = transaction {
    Users.update({ Users.username eq username }) {
        it[activated] = true
    }
}

fun createDummyEntryRef(source: String, target: String, origin: String) = transaction {
    EntryRefs.insert {
        it[this.sourceEntryId] = source
        it[this.targetEntryId] = target
        it[this.originId] = origin
    }
}
