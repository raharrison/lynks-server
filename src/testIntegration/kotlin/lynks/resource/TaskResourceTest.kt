package lynks.resource

import io.restassured.RestAssured.post
import lynks.common.BaseProperties
import lynks.common.EntryType
import lynks.common.ServerTest
import lynks.common.TaskDefinition
import lynks.task.link.LinkProcessingTask
import lynks.util.createDummyEntry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TaskResourceTest: ServerTest() {

    @BeforeEach
    fun createEntries() {
        val props = BaseProperties()
        props.addTask(TaskDefinition("t1", "dummy", LinkProcessingTask::class.qualifiedName!!))
        createDummyEntry("e11", "title1", "https://ryanharrison.co.uk", EntryType.LINK, props)
    }

    @Test
    fun testInvalidEntryId() {
        post("/entry/{eid}/task/{tid}", "invalid", "t1")
                .then()
                .statusCode(404)
    }

    @Test
    fun testInvalidTaskId() {
        post("/entry/{eid}/task/{tid}", "e11", "invalid")
                .then()
                .statusCode(404)
    }

    @Test
    fun testRunValidTask() {
        post("/entry/{eid}/task/{tid}", "e11", "t1")
                .then()
                .statusCode(200)
    }

}
