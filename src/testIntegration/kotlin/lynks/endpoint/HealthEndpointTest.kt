package lynks.endpoint

import io.restassured.RestAssured.get
import lynks.common.ServerTest
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.matchesPattern
import org.junit.jupiter.api.Test

class HealthEndpointTest: ServerTest() {

    @Test
    fun testHealthResponse() {
        get("/health")
                .then()
                .statusCode(200)
            .body("status", equalTo("up"))
            .body("application", equalTo("lynks"))
            .body("version", matchesPattern("""\d+\.\d+\.\d+"""))
    }

}
