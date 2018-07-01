package util

import comment.Comments
import common.Entries
import common.EntryType
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import tag.Tags

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

fun createDummyTag(id: String, name: String, parentId: String?=null) = transaction{
    Tags.insert {
        it[Tags.id] = id
        it[Tags.name] = name
        it[Tags.parentId] = parentId
        it[dateUpdated] = System.currentTimeMillis()
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