package common

import db.DatabaseFactory
import org.junit.jupiter.api.BeforeEach

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

}