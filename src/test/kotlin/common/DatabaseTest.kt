package common

import db.DatabaseFactory
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import tag.Tags

abstract class DatabaseTest {

    companion object {
        val databaseFactory = DatabaseFactory()
    }

    @BeforeEach
    fun before() {
        if(!databaseFactory.connected) {
            databaseFactory.connect()
        }
        databaseFactory.resetAll()
    }

    protected fun createDummyEntry(id: String, title: String, content: String, type: EntryType) = transaction {
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

    protected fun createDummyTag(id: String, name: String, parentId: String?=null) = transaction{
        Tags.insert({
            it[Tags.id] = id
            it[Tags.name] = name
            it[Tags.parentId] = parentId
            it[Tags.dateUpdated] = System.currentTimeMillis()
        })

    }
}