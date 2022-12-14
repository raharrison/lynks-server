package lynks.endpoint

import io.restassured.RestAssured.*
import io.restassured.http.ContentType
import lynks.common.EntryType
import lynks.common.Environment
import lynks.common.ServerTest
import lynks.resource.ImageUploadErrorResponse
import lynks.resource.ImageUploadResponse
import lynks.resource.Resource
import lynks.resource.ResourceType
import lynks.util.createDummyEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ResourceEndpointTest : ServerTest() {

    @BeforeEach
    fun createEntries() {
        createDummyEntry("e1", "title1", "content1", EntryType.LINK)
    }

    @Test
    fun testUploadImage() {
        val filename = "image.png"
        val content = byteArrayOf(1,2,3,4)
        val result = given()
            .multiPart("file", filename, content)
            .When()
            .post("/imageUpload")
            .then()
            .statusCode(200)
            .extract().to<ImageUploadResponse>()
        assertThat(result.data.filePath).isNotEmpty()
        val path = result.data.filePath.removePrefix(Environment.server.rootPath)
        val resource = get(path)
            .then()
            .statusCode(200)
            .extract().asByteArray()
        assertThat(resource).isEqualTo(content)
    }

    @Test
    fun testUploadImageNoContent() {
        val result = given()
            .When()
            .header("Content-Type", "multipart/form-data; boundary=&")
            .post("/imageUpload")
            .then()
            .statusCode(400)
            .extract().to<ImageUploadErrorResponse>()
        assertThat(result.error).isEqualTo("noFileGiven")
    }

    @Test
    fun testUploadImageInvalidType() {
        val filename = "image.pdf"
        val content = byteArrayOf(1,2,3,4)
        val result = given()
            .multiPart("file", filename, content)
            .When()
            .post("/imageUpload")
            .then()
            .statusCode(415)
            .extract().to<ImageUploadErrorResponse>()
        assertThat(result.error).isEqualTo("typeNotAllowed")
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
                .post("/entry/{entryId}/resource", "e1")
                .then()
                .statusCode(400)
    }

    @Test
    fun testCreateResourceNoMultipart() {
        post("/entry/{entryId}/resource", "e1")
                .then()
                .statusCode(500)
    }

    @Test
    fun testGetResourcesForEntry() {
        val filename = "attachment.txt"
        val content = byteArrayOf(1,2,3,4)
        val generated = uploadResource(filename, content)
        val resources = get("/entry/{entryId}/resource", generated.entryId)
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
        val resources = get("/entry/{entryId}/resource", "invalid")
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

        val resource = get("/entry/{entryId}/resource/{id}", generated.entryId, generated.id)
                .then()
                .statusCode(200)
                .header("Content-Disposition", "inline; filename=\"$filename\"")
                .extract().asByteArray()
        assertThat(resource).isEqualTo(content)
    }

    @Test
    fun testGetInvalidResourceFile() {
        get("/entry/{entryId}/resource/{id}", "e1", "invalid")
                .then()
                .statusCode(404)
    }

    @Test
    fun testGetResource() {
        val filename = "attachment.txt"
        val content = byteArrayOf(1,2,3,4)
        val generated = uploadResource(filename, content)

        val resource = get("/entry/{entryId}/resource/{id}/info", generated.entryId, generated.id)
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
        get("/entry/{entryId}/resource/{id}/info", "e1", "invalid")
            .then()
            .statusCode(404)
    }

    @Test
    fun testUpdateResource() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val generated = uploadResource("content.txt", data)
        val originalResource = get("/entry/{entryId}/resource/{id}/info", generated.entryId, generated.id)
            .then()
            .statusCode(200)
            .extract().`as`(Resource::class.java)
        assertThat(originalResource.name).isEqualTo("content.txt")
        assertThat(originalResource.type).isEqualTo(ResourceType.UPLOAD)
        assertThat(originalResource.size).isEqualTo(data.size.toLong())
        assertThat(originalResource.extension).isEqualTo("txt")
        assertThat(originalResource.entryId).isEqualTo("e1")

        val updateResourceRequest = originalResource.copy(name="updated.xml")
        val updatedResource = given()
            .contentType(ContentType.JSON)
            .body(updateResourceRequest)
            .When()
            .put("/entry/{entryId}/resource", generated.entryId)
            .then()
            .statusCode(200)
            .extract().to<Resource>()
        assertThat(updatedResource.name).isEqualTo("updated.xml")
        assertThat(updatedResource.version).isOne()
        assertThat(updatedResource.type).isEqualTo(ResourceType.UPLOAD)
        assertThat(updatedResource.size).isEqualTo(data.size.toLong())
        assertThat(updatedResource.extension).isEqualTo("xml")
        assertThat(updatedResource.entryId).isEqualTo(generated.entryId)

        val updatedResourceRetrieval = get("/entry/{entryId}/resource/{id}/info", generated.entryId, generated.id)
            .then()
            .statusCode(200)
            .extract().`as`(Resource::class.java)
        assertThat(updatedResourceRetrieval).isEqualTo(updatedResource)

        val resourceContents = get("/entry/{entryId}/resource/{id}", generated.entryId, generated.id)
            .then()
            .statusCode(200)
            .extract().asByteArray()
        assertThat(resourceContents).isEqualTo(data)
    }

    @Test
    fun testUpdateInvalidResource() {
        val invalid = Resource("invalid", "pid", "eid", 1, "file1.txt", "txt", ResourceType.UPLOAD,
            12L, 1234L)
        given()
            .contentType(ContentType.JSON)
            .body(invalid)
            .When()
            .put("/entry/{entryId}/resource", invalid.entryId)
            .then()
            .statusCode(404)
    }

    @Test
    fun testDeleteResource() {
        val generated = uploadResource("attachment.txt", byteArrayOf(1,2,3,4,5))
        delete("/entry/{entryId}/resource/{id}", generated.entryId, generated.id)
                .then()
                .statusCode(200)
        get("/entry/{entryId}/resource/{id}", generated.entryId, generated.id)
                .then()
                .statusCode(404)
    }

    @Test
    fun testDeleteInvalidResource() {
        delete("/entry/{entryId}/resource/{id}", "e1", "invalid")
                .then()
                .statusCode(404)
    }

    private fun uploadResource(filename: String, content: ByteArray): Resource {
        return given()
                .multiPart("file", filename, content)
                .When()
                .post("/entry/{entryId}/resource", "e1")
                .then()
                .statusCode(201)
                .extract().to()
    }


}
