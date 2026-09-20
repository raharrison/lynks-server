package lynks.endpoint

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.restassured.RestAssured.get
import io.restassured.RestAssured.given
import lynks.common.Environment
import lynks.common.ServerTest
import lynks.suggest.Suggestion
import lynks.util.FileUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

class SuggestionEndpointTest: ServerTest() {

    private lateinit var wireMockServer: WireMockServer

    @BeforeEach
    fun beforeEach() {
        wireMockServer = WireMockServer(WireMockConfiguration.options().port(3893))
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
            .statusCode(422)
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
            .statusCode(422)
    }

    @Test
    fun testYoutubeSuggestion() {
        val videoId = "JGvk4M0Rfxo"
        val thumbnailBytes = byteArrayOf(1, 2, 3)
        val previewBytes = byteArrayOf(4, 5, 6)
        val apiResponse = """
            {
              "items": [{
                "snippet": {
                  "title": "Welcome to the Kotlin YouTube Channel!",
                  "description": "Official Kotlin channel.",
                  "tags": ["kotlin", "jvm"],
                  "channelTitle": "Kotlin by JetBrains",
                  "publishedAt": "2020-05-01T10:00:00Z",
                  "thumbnails": {
                    "medium":  { "url": "http://localhost:3893/vi/$videoId/mqdefault.jpg" },
                    "maxres":  { "url": "http://localhost:3893/vi/$videoId/maxresdefault.jpg" }
                  }
                }
              }]
            }
        """.trimIndent()
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/youtube/v3/videos"))
                .willReturn(ok(apiResponse).withHeader("Content-Type", "application/json"))
        )
        wireMockServer.stubFor(
            WireMock.get("/vi/$videoId/mqdefault.jpg")
                .willReturn(ok().withBody(thumbnailBytes))
        )
        wireMockServer.stubFor(
            WireMock.get("/vi/$videoId/maxresdefault.jpg")
                .willReturn(ok().withBody(previewBytes))
        )

        val suggestion = given()
            .body("https://www.youtube.com/watch?v=$videoId")
            .post("/suggest")
            .then()
            .statusCode(200)
            .extract().`as`(Suggestion::class.java)

        assertThat(suggestion.url).isEqualTo("https://www.youtube.com/watch?v=$videoId")
        assertThat(suggestion.title).isEqualTo("Welcome to the Kotlin YouTube Channel!")
        assertThat(suggestion.keywords).containsExactlyInAnyOrder("kotlin", "jvm")
        assertThat(suggestion.preview).isNotNull()
        assertThat(suggestion.thumbnail).isNotNull()

        retrieveTempResource(suggestion.thumbnail)
        retrieveTempResource(suggestion.preview)
    }

    @Test
    fun testYoutubeSuggestionApiFailure() {
        val videoId = "JGvk4M0Rfxo"
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/youtube/v3/videos"))
                .willReturn(status(500))
        )
        given()
            .body("https://www.youtube.com/watch?v=$videoId")
            .post("/suggest")
            .then()
            .statusCode(422)
    }

    private fun retrieveTempResource(path: String?) {
        assertThat(get("/temp/$path")
                .then()
                .statusCode(200)
                .extract().asByteArray()).isNotEmpty()
    }

}
