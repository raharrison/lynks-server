package lynks.common

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.restassured.RestAssured.post
import io.restassured.common.mapper.TypeRef
import io.restassured.config.ObjectMapperConfig.objectMapperConfig
import io.restassured.config.RestAssuredConfig
import io.restassured.http.ContentType
import io.restassured.response.ResponseBodyExtractionOptions
import io.restassured.specification.RequestSpecification
import lynks.db.DatabaseFactory
import lynks.module
import lynks.util.JsonMapper
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import java.util.concurrent.TimeUnit

open class ServerTest {

    protected fun RequestSpecification.When(): RequestSpecification {
        return this.`when`()
    }

    protected inline fun <reified T> ResponseBodyExtractionOptions.to(): T {
        return this.`as`(object : TypeRef<T>() {})
    }

    companion object {

        private var serverStarted = false

        private lateinit var server: EmbeddedServer<*, *>

        @BeforeAll
        @JvmStatic
        fun startServer() {
            if(!serverStarted) {
                TestPostgresContainer.configure()
                server = embeddedServer(Netty, port = Environment.server.port, host = "127.0.0.1", module = Application::module)
                server.start()
                serverStarted = true

                RestAssured.enableLoggingOfRequestAndResponseIfValidationFails()
                RestAssured.baseURI = "http://localhost"
                RestAssured.basePath = Environment.server.rootPath
                RestAssured.port = Environment.server.port
                RestAssured.config = RestAssuredConfig.config().objectMapperConfig(objectMapperConfig()
                        .jackson2ObjectMapperFactory { _, _ -> JsonMapper.defaultMapper })
                RestAssured.requestSpecification = given().accept(ContentType.JSON)
                RestAssured.enableLoggingOfRequestAndResponseIfValidationFails()

                Runtime.getRuntime().addShutdownHook(Thread { server.stop(0, 0, TimeUnit.SECONDS) })
            }
        }

        // already connected via server
        private val databaseFactory = DatabaseFactory()
    }

    @BeforeEach
    fun before() = databaseFactory.resetAll()

    protected fun refreshGroups() {
        post("/tag/refresh")
        post("/collection/refresh")
    }

}

