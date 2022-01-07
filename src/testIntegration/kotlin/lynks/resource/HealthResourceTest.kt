package lynks.resource

import io.restassured.RestAssured.get
import lynks.common.ServerTest
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

    @Test
    fun testInfo() {
        get("/info")
            .then()
            .statusCode(200)
            .body("version", equalTo(""))
    }

}
