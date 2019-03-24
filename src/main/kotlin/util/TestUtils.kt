package util

import comment.Comments
import common.BaseProperties
import common.Entries
import common.EntryType
import group.GroupType
import group.Groups
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import reminder.ReminderType
import reminder.Reminders
import worker.WorkerSchedules
import java.time.ZoneId

fun createDummyEntry(id: String, title: String, content: String, type: EntryType, prop: BaseProperties? = null) = transaction {
    Entries.insert {
        it[Entries.id] = id
        it[Entries.title] = title
        it[plainContent] = content
        it[Entries.content] = content
        it[src] = "src"
        it[Entries.type] = type
        it[dateUpdated] = System.currentTimeMillis()
        it[props] = prop
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
    Comments.insert {
        it[Comments.id] = id
        it[Comments.entryId] = entryId
        it[plainText] = content
        it[markdownText] = content
        it[dateCreated] = System.currentTimeMillis()
    }
}

fun createDummyReminder(id: String, entryId: String, type: ReminderType, message: String? = null, spec: String, tz: String = ZoneId.systemDefault().id) = transaction {
    Reminders.insert {
        it[Reminders.reminderId] = id
        it[Reminders.entryId] = entryId
        it[Reminders.type] = type
        it[Reminders.message] = message
        it[Reminders.spec] = spec
        it[Reminders.tz] = tz
    }
}

fun createDummyWorkerSchedule(worker: String, key: String, request: Any) = transaction {
    WorkerSchedules.insert {
        it[WorkerSchedules.worker] = worker
        it[WorkerSchedules.key] = key
        it[WorkerSchedules.request] = JsonMapper.defaultMapper.writeValueAsString(request)
    }
}