package util

import comment.Comments
import common.Entries
import common.EntryType
import group.Collections
import group.Tags
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import schedule.ScheduleType
import schedule.Schedules
import java.time.ZoneId

fun createDummyEntry(id: String, title: String, content: String, type: EntryType) = transaction {
    Entries.insert {
        it[Entries.id] = id
        it[Entries.title] = title
        it[plainContent] = content
        it[Entries.content] = content
        it[src] = "src"
        it[Entries.type] = type
        it[dateUpdated] = System.currentTimeMillis()
    }
}

fun createDummyTag(id: String, name: String) = transaction{
    val time = System.currentTimeMillis()
    Tags.insert {
        it[Tags.id] = id
        it[Tags.name] = name
        it[dateUpdated] = time
        it[dateCreated] = time
    }
}

fun createDummyCollection(id: String, name: String, parentId: String?=null) = transaction {
    val time = System.currentTimeMillis()
    Collections.insert {
        it[Collections.id] = id
        it[Collections.name] = name
        it[Collections.parentId] = parentId
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

fun createDummyReminder(id: String, entryId: String, type: ScheduleType, spec: String, tz: String = ZoneId.systemDefault().id) = transaction {
    Schedules.insert {
        it[Schedules.scheduleId] = id
        it[Schedules.entryId] = entryId
        it[Schedules.type] = type
        it[Schedules.spec] = spec
        it[Schedules.tz] = tz
    }
}