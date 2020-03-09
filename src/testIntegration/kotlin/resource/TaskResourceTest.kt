package resource

import common.BaseProperties
import common.EntryType
import common.ServerTest
import common.TaskDefinition
import io.restassured.RestAssured.post
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import task.LinkProcessingTask
import util.createDummyEntry

class TaskResourceTest: ServerTest() {

    @BeforeEach
    fun createEntries() {
        val props = BaseProperties()
        props.addTask(TaskDefinition("t1", "dummy", LinkProcessingTask::class.qualifiedName!!))
        createDummyEntry("e1", "title1", "ryanharrison.co.uk", EntryType.LINK, props)
    }

    @Test
    fun testInvalidEntryId() {
        post("/entry/{eid}/tasks/{tid}", "invalid", "t1")
                .then()
                .statusCode(400)
    }

    @Test
    fun testInvalidTaskId() {
        post("/entry/{eid}/tasks/{tid}", "e1", "invalid")
                .then()
                .statusCode(400)
    }

    @Test
    fun testRunValidTask() {
        post("/entry/{eid}/tasks/{tid}", "e1", "t1")
                .then()
                .statusCode(200)
    }

}