package common

import db.DatabaseFactory
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before

abstract class DatabaseTest {

    companion object {
        val databaseFactory = DatabaseFactory()
    }

    @Before
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
            it[src] = "src"
            it[Entries.type] = type
            it[dateUpdated] = System.currentTimeMillis()
        }
    }
}