package common

import db.DatabaseFactory
import io.ktor.application.Application
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.restassured.RestAssured
import io.restassured.config.ObjectMapperConfig.objectMapperConfig
import io.restassured.config.RestAssuredConfig
import io.restassured.specification.RequestSpecification
import module
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import util.JsonMapper
import java.util.concurrent.TimeUnit

open class ServerTest {

    protected fun RequestSpecification.When(): RequestSpecification {
        return this.`when`()
    }

    companion object {

        private var serverStarted = false

        private lateinit var server: ApplicationEngine
        val baseUrl = "http://localhost:${Environment.port}"

        @BeforeAll
        @JvmStatic
        fun startServer() {
            if(!serverStarted) {
                server = embeddedServer(Netty, Environment.port, watchPaths = listOf("Main"), module = Application::module)
                server.start()
                serverStarted = true

                RestAssured.baseURI = "http://localhost"
                RestAssured.port = Environment.port
                RestAssured.config = RestAssuredConfig.config().objectMapperConfig(objectMapperConfig()
                        .jackson2ObjectMapperFactory { _, _ -> JsonMapper.defaultMapper })

                Runtime.getRuntime().addShutdownHook(Thread { server.stop(0, 0, TimeUnit.SECONDS) })
            }
        }

        // already connected via server
        private val databaseFactory = DatabaseFactory()
    }

    @BeforeEach
    fun before() = databaseFactory.resetAll()

}

