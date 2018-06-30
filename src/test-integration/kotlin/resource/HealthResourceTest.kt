package resource

import common.ServerTest
import io.restassured.RestAssured.get
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test

class HealthResourceTest: ServerTest() {

    @Test
    fun testOkResponse() {
        get("/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("ok"))
    }

}