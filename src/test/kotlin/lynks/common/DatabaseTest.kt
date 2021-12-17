package lynks.common

import lynks.db.DatabaseFactory
import org.junit.jupiter.api.BeforeEach

abstract class DatabaseTest {

    companion object {
        val databaseFactory = DatabaseFactory()
    }

    @BeforeEach
    fun before() {
        if(!databaseFactory.connected) {
            databaseFactory.connectAndMigrate()
        }
        databaseFactory.resetAll()
    }

}
