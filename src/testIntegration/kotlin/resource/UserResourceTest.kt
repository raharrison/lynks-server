package resource

import common.ServerTest
import io.restassured.RestAssured.get
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import user.Preferences

class UserResourceTest : ServerTest() {

    @Test
    fun testUpdatePreferences() {
        val preferences = Preferences("email@example.com", true)
        val created = given()
                .contentType(ContentType.JSON)
                .body(preferences)
                .When()
                .post("/user/preferences")
                .then()
                .extract().to<Preferences>()
        assertThat(preferences).isEqualTo(created)

        // and retrieve after update
        val retrieved = get("/user/preferences")
                .then()
                .statusCode(200)
                .extract().to<Preferences>()
        assertThat(preferences).isEqualTo(retrieved)
    }

    @Test
    fun testUpdateInvalidEmail() {
        val preferences = Preferences("invalid", true)
        given()
                .contentType(ContentType.JSON)
                .body(preferences)
                .When()
                .post("/user/preferences")
                .then()
                .statusCode(400)
    }
}