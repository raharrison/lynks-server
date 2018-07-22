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
        assertThat(suggestion.url).isEqualTo("https://ryanharrison.co.uk/")
        assertThat(suggestion.title).isEqualTo("Ryan Harrison - My blog, portfolio and technology related ramblings")
        assertThat(suggestion.screenshot).isNotNull()
        assertThat(suggestion.thumbnail).isNotNull()

        retrieveTempResource(suggestion.screenshot)
        retrieveTempResource(suggestion.thumbnail)
    }

    @Test
    fun testYoutubeSuggestion() {
        val suggestion = given()
                .body("https://www.youtube.com/watch?v=DAiEUeM8Uv0")
                .post("/suggest")
                .then()
                .extract().`as`(Suggestion::class.java)
        assertThat(suggestion.url).isEqualTo("https://www.youtube.com/watch?v=DAiEUeM8Uv0")
        assertThat(suggestion.title).isEqualTo("Savoy - How U Like Me Now (feat. Roniit) [Monstercat Release]")
        assertThat(suggestion.screenshot).isNull()
        assertThat(suggestion.thumbnail).isNotNull()

        retrieveTempResource(suggestion.thumbnail)
    }

    private fun retrieveTempResource(path: String?) {
        assertThat(get("/temp/$path")
                .then()
                .statusCode(200)
                .extract().asByteArray()).isNotEmpty()
    }

}