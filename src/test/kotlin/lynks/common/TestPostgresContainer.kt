package lynks.common

import lynks.util.RandomUtils
import org.testcontainers.containers.PostgreSQLContainer

object TestPostgresContainer {

    private val container: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer("postgres:18")
            .withDatabaseName("lynksdb")
            .withUsername("lynksuser")
            .withPassword(RandomUtils.generateUid())
            .also { it.start() }
    }

    fun configure() {
        val c = container
        System.setProperty("database.url", c.jdbcUrl)
        System.setProperty("database.user", c.username)
        System.setProperty("database.password", c.password)
    }
}
