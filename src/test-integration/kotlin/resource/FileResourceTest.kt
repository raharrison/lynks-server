package resource

import common.EntryType
import common.ServerTest
import io.restassured.RestAssured.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import util.createDummyEntry

class FileResourceTest : ServerTest() {

    @BeforeEach
    fun createEntries() {
        createDummyEntry("e1", "title1", "content1", EntryType.LINK)
    }

    @Test
    fun testCreateResource() {
        val filename = "attachment.txt"
        val content = byteArrayOf(1,2,3,4)
        val resource = uploadResource(filename, content)

        assertThat(resource.name).isEqualTo(filename)
        assertThat(resource.type).isEqualTo(ResourceType.UPLOAD)
        assertThat(resource.size).isEqualTo(content.size.toLong())
        assertThat(resource.extension).isEqualTo("txt")
        assertThat(resource.entryId).isEqualTo("e1")
    }

    @Test
    fun testCreateResourceNoFile() {
        given()
                .multiPart("file", "body", "text/plain")
                .When()
                .post("/entry/{entryId}/resources", "e1")
                .then()
                .statusCode(400)
    }

    @Test
    fun testCreateResourceNoMultipart() {
        post("/entry/{entryId}/resources", "e1")
                .then()
                .statusCode(500)
    }

    @Test
    fun testGetResourcesForEntry() {
        val filename = "attachment.txt"
        val content = byteArrayOf(1,2,3,4)
        val generated = uploadResource(filename, content)
        val resources = get("/entry/{entryId}/resources", generated.entryId)
                .then()
                .statusCode(200)
                .extract().`as`(Array<Resource>::class.java)

        assertThat(resources).hasSize(1)
        resources.first().also {
            assertThat(it.name).isEqualTo(filename)
            assertThat(it.type).isEqualTo(ResourceType.UPLOAD)
            assertThat(it.size).isEqualTo(content.size.toLong())
            assertThat(it.extension).isEqualTo("txt")
            assertThat(it.entryId).isEqualTo("e1")
        }
    }

    @Test
    fun testGetResourcesForEntryDoesntExist() {
        val resources = get("/entry/{entryId}/resources", "invalid")
                .then()
                .statusCode(200)
                .extract().to<List<Resource>>()
        assertThat(resources).isEmpty()
    }

    @Test
    fun testGetResourceFile() {
        val filename = "attachment.txt"
        val content = byteArrayOf(1,2,3,4)
        val generated = uploadResource(filename, content)

        val resource = get("/entry/{entryId}/resources/{id}", generated.entryId, generated.id)
                .then()
                .statusCode(200)
                .header("Content-Disposition", "attachment; filename=\"$filename\"")
                .extract().asByteArray()
        assertThat(resource).isEqualTo(content)
    }

    @Test
    fun testGetInvalidResourceFile() {
        get("/entry/{entryId}/resources/{id}", "e1", "invalid")
                .then()
                .statusCode(404)
    }

    @Test
    fun testGetResource() {
        val filename = "attachment.txt"
        val content = byteArrayOf(1,2,3,4)
        val generated = uploadResource(filename, content)

        val resource = get("/entry/{entryId}/resources/{id}/info", generated.entryId, generated.id)
            .then()
            .statusCode(200)
            .header("X-Resource-Mime-Type", "text/plain")
            .extract().`as`(Resource::class.java)
        assertThat(resource.name).isEqualTo(filename)
        assertThat(resource.type).isEqualTo(ResourceType.UPLOAD)
        assertThat(resource.size).isEqualTo(content.size.toLong())
        assertThat(resource.extension).isEqualTo("txt")
        assertThat(resource.entryId).isEqualTo("e1")
    }

    @Test
    fun testGetInvalidResource() {
        get("/entry/{entryId}/resources/{id}/info", "e1", "invalid")
            .then()
            .statusCode(404)
    }

    @Test
    fun testDeleteResource() {
        val generated = uploadResource("attachment.txt", byteArrayOf(1,2,3,4,5))
        delete("/entry/{entryId}/resources/{id}", generated.entryId, generated.id)
                .then()
                .statusCode(200)
        get("/entry/{entryId}/resources/{id}", generated.entryId, generated.id)
                .then()
                .statusCode(404)
    }

    @Test
    fun testDeleteInvalidResource() {
        delete("/entry/{entryId}/resources/{id}", "e1", "invalid")
                .then()
                .statusCode(404)
    }

    private fun uploadResource(filename: String, content: ByteArray): Resource {
        return given()
                .multiPart("file", filename, content)
                .When()
                .post("/entry/{entryId}/resources", "e1")
                .then()
                .statusCode(201)
                .extract().to()
    }


}