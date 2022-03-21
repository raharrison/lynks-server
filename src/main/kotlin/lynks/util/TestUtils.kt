package lynks.util

import lynks.comment.Comments
import lynks.common.BaseProperties
import lynks.common.Entries
import lynks.common.EntryType
import lynks.group.GroupType
import lynks.group.Groups
import lynks.notify.NotificationMethod
import lynks.reminder.ReminderType
import lynks.reminder.Reminders
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
                        message: String? = null, spec: String, tz: String = ZoneId.systemDefault().id) = transaction {
    val time = System.currentTimeMillis()
    Reminders.insert {
        it[Reminders.reminderId] = id
        it[Reminders.entryId] = entryId
        it[Reminders.type] = type
        it[Reminders.notifyMethods] = notifyMethods.joinToString(",")
        it[Reminders.message] = message
        it[Reminders.spec] = spec
        it[Reminders.tz] = tz
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
