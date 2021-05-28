package resource

import common.ServerTest
import io.restassured.RestAssured.get
import io.restassured.RestAssured.given
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import suggest.Suggestion

class SuggestionResourceTest: ServerTest() {

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
