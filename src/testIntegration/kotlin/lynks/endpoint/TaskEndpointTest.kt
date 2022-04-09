package lynks.endpoint

import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import lynks.common.*
import lynks.task.link.LinkProcessingTask
import lynks.util.createDummyEntry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TaskEndpointTest : ServerTest() {

    @BeforeEach
    fun createEntries() {
        val props = BaseProperties()
        props.addTask(
            TaskDefinition(
                "t1", "dummy", LinkProcessingTask::class.qualifiedName!!,
                listOf(TaskParameter("k1", TaskParameterType.TEXT))
            )
        )
        createDummyEntry("e11", "title1", "https://ryanharrison.co.uk", EntryType.LINK, props)
    }

    @Test
    fun testInvalidEntryId() {
        given()
            .contentType(ContentType.JSON)
            .body(mapOf("key" to "val"))
            .When()
            .post("/entry/{eid}/task/{tid}", "invalid", "t1")
            .then()
            .statusCode(404)
    }

    @Test
    fun testInvalidTaskId() {
        given()
            .contentType(ContentType.JSON)
            .body(mapOf("key" to "val"))
            .When()
            .post("/entry/{eid}/task/{tid}", "e11", "invalid")
            .then()
            .statusCode(404)
    }

    @Test
    fun testInvalidTaskParamFormat() {
        given()
            .contentType(ContentType.JSON)
            .body(mapOf("k1" to mapOf("k2" to "v1")))
            .When()
            .post("/entry/{eid}/task/{tid}", "e11", "invalid")
            .then()
            .statusCode(400)
    }

    @Test
    fun testMissingTaskParam() {
        given()
            .contentType(ContentType.JSON)
            .body(mapOf("k2" to "v2"))
            .When()
            .post("/entry/{eid}/task/{tid}", "e11", "t1")
            .then()
            .statusCode(400)
    }


    @Test
    fun testAcceptsNullParams() {
        given()
            .contentType(ContentType.JSON)
            .body(mapOf("k1" to "v1", "k2" to null))
            .When()
            .post("/entry/{eid}/task/{tid}", "e11", "t1")
            .then()
            .statusCode(200)
    }

    @Test
    fun testRunValidTask() {
        given()
            .contentType(ContentType.JSON)
            .body(mapOf("k1" to "v1"))
            .When()
            .post("/entry/{eid}/task/{tid}", "e11", "t1")
            .then()
            .statusCode(200)
    }

}
