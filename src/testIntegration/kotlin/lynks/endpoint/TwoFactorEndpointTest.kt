package lynks.endpoint

import io.restassured.RestAssured.get
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import lynks.common.Environment
import lynks.common.ServerTest
import lynks.user.TwoFactorUpdateRequest
import lynks.user.TwoFactorValidateRequest
import lynks.util.createDummyUser
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasLength
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TwoFactorEndpointTest : ServerTest() {

    @BeforeEach
    fun setup() {
        createDummyUser(Environment.auth.defaultUserName, "user1@mail.com", "Bob Smith")
    }

    @Test
    fun testGetTwoFactorEnabled() {
        get("/user/2fa")
            .then()
            .statusCode(200)
            .body("enabled", equalTo(false))

        given()
            .contentType(ContentType.JSON)
            .body(TwoFactorUpdateRequest(true))
            .When()
            .put("/user/2fa")
            .then()
            .statusCode(200)

        get("/user/2fa")
            .then()
            .statusCode(200)
            .body("enabled", equalTo(true))
    }

    @Test
    fun testGetTwoFactorSecret() {
        get("/user/2fa/secret")
            .then()
            .statusCode(200)
            .body("secret", equalTo(""))

        given()
            .contentType(ContentType.JSON)
            .body(TwoFactorUpdateRequest(true))
            .When()
            .put("/user/2fa")
            .then()
            .statusCode(200)

        get("/user/2fa/secret")
            .then()
            .statusCode(200)
            .body("secret", hasLength(16))
    }

    @Test
    fun testValidateTotp() {
        given()
            .contentType(ContentType.JSON)
            .body(TwoFactorValidateRequest("code"))
            .When()
            .post("/user/2fa/validate")
            .then()
            .statusCode(200)
            .body("valid", equalTo(false))
    }


}
