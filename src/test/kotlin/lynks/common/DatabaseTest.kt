package lynks.common

import lynks.db.DatabaseFactory
import lynks.util.TEST_USER
import lynks.util.createDummyUser
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
        createDummyUser("test-user", id = TEST_USER)
    }

}
