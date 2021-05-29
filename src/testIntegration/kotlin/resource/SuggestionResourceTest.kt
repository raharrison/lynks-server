package resource

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import common.Environment
import common.ServerTest
import io.restassured.RestAssured.get
import io.restassured.RestAssured.given
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import suggest.Suggestion
import util.FileUtils
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

class SuggestionResourceTest: ServerTest() {

    private lateinit var wireMockServer: WireMockServer

    @BeforeEach
    fun beforeEach() {
        wireMockServer = WireMockServer(
            WireMockConfiguration.options()
                .port(3893)
        )
        wireMockServer.start()
    }

    @AfterEach
    fun afterEach() {
        wireMockServer.stop()
        wireMockServer.resetAll()
        Paths.get(Environment.resource.resourceBasePath).toFile().deleteRecursively()
        Paths.get(Environment.resource.resourceTempPath).toFile().deleteRecursively()
    }

    @Test
    fun testInvalidUrlReturnsBadRequest() {
        given()
                .body("invalid")
                .post("/suggest")
                .then()
                    .statusCode(400)
    }

    @Test
    fun testValidUrlInvalidNavigation() {
        given()
                .body("http://invalidname.fo/")
                .post("/suggest")
                .then()
                .statusCode(500)
    }

    @Test
    fun testGetSuggestion() {
        val thumbPath = Paths.get(Environment.resource.resourceTempPath, "e1", "t1.jpg")
        FileUtils.writeToFile(thumbPath, byteArrayOf(1, 2, 3))
        val previewPath = Paths.get(Environment.resource.resourceTempPath, "e1", "p1.jpg")
        FileUtils.writeToFile(previewPath, byteArrayOf(4, 5, 6))
        val generatedResources = """
            {
                "details": {
                    "url": "https://ryanharrison.co.uk",
                    "title": "Ryan Harrison - My blog, portfolio and technology related ramblings",
                    "keywords": ["first", "second"]
                },
                "resources": [
                    {
                        "resourceType": "PREVIEW",
                        "targetPath": "${previewPath.absolutePathString().replace("\\", "\\\\")}",
                        "extension": "jpg"
                    },
                    {
                        "resourceType": "THUMBNAIL",
                        "targetPath": "${thumbPath.absolutePathString().replace("\\", "\\\\")}",
                        "extension": "jpg"
                    }
                ]
            }
        """.trimIndent()
        wireMockServer.stubFor(
            WireMock.post("/api/suggest")
                .withHeader("Content-Type", WireMock.equalTo("application/json"))
                .willReturn(ok(generatedResources))
        )
        val suggestion = given()
                .body("https://ryanharrison.co.uk")
                .post("/suggest")
                .then()
                .extract().`as`(Suggestion::class.java)
        assertThat(suggestion.url).isEqualTo("https://ryanharrison.co.uk")
        assertThat(suggestion.title).isEqualTo("Ryan Harrison - My blog, portfolio and technology related ramblings")
        assertThat(suggestion.preview).isNotNull()
        assertThat(suggestion.thumbnail).isNotNull()
        assertThat(suggestion.keywords).isNotEmpty()

        retrieveTempResource(suggestion.preview)
        retrieveTempResource(suggestion.thumbnail)
    }

    @Test
    fun testGetSuggestionExternalCallFailure() {
        wireMockServer.stubFor(
            WireMock.post("/api/suggest")
                .willReturn(status(500))
        )
        given()
            .body("https://deepu.tech/memory-management-in-jvm/")
            .post("/suggest")
            .then()
            .statusCode(500)
    }

    @Test
    fun testYoutubeSuggestion() {
        val suggestion = given()
                .body("https://www.youtube.com/watch?v=JGvk4M0Rfxo")
                .post("/suggest")
                .then()
                .extract().`as`(Suggestion::class.java)

        assertThat(suggestion.url).isEqualTo("https://www.youtube.com/watch?v=JGvk4M0Rfxo")
        assertThat(suggestion.title).isEqualTo("Welcome to Kotlin by JetBrains!")
        assertThat(suggestion.preview).isNotNull()
        assertThat(suggestion.thumbnail).isNotNull()
        assertThat(suggestion.keywords).isEmpty()

        retrieveTempResource(suggestion.thumbnail)
    }

    private fun retrieveTempResource(path: String?) {
        assertThat(get("/temp/$path")
                .then()
                .statusCode(200)
                .extract().asByteArray()).isNotEmpty()
    }

}
