package lynks.endpoint

import io.restassured.RestAssured.*
import io.restassured.http.ContentType
import lynks.common.EntryType
import lynks.common.File
import lynks.common.NewFile
import lynks.common.ServerTest
import lynks.common.page.Page
import lynks.util.createDummyCollection
import lynks.util.createDummyEntry
import lynks.util.createDummyTag
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FileEndpointTest : ServerTest() {

    @BeforeEach
    fun createEntries() {
        createDummyEntry("e1", "title1", "content1", EntryType.LINK)
        Thread.sleep(10)// prevent having same creation timestamp
        createDummyEntry("e2", "title2", "content2", EntryType.FILE)
        Thread.sleep(10)
        createDummyEntry("e3", "title3", "content3", EntryType.FILE)
        createDummyTag("t1", "tag1")
        createDummyCollection("c1", "col1")
        post("/tag/refresh")
        post("/collection/refresh")
    }

    @Test
    fun testCreateFile() {
        val newFile = NewFile(null, "title4", listOf("t1"), listOf("c1"))
        val created = given()
            .contentType(ContentType.JSON)
            .body(newFile)
            .When()
            .post("/file")
            .then()
            .statusCode(201)
            .extract().to<File>()
        assertThat(created.title).isEqualTo(newFile.title)
        assertThat(created.type).isEqualTo(EntryType.FILE)
        assertThat(created.tags).hasSize(1).extracting("id").containsExactly("t1")
        assertThat(created.collections).hasSize(1).extracting("id").containsExactly("c1")
        assertThat(created.dateCreated).isEqualTo(created.dateUpdated)
        val retrieved = get("/file/{id}", created.id)
            .then()
            .extract().to<File>()
        assertThat(created).usingRecursiveComparison().ignoringFields("props").isEqualTo(retrieved)
    }

    @Test
    fun testCreateFileWithInvalidGroups() {
        given()
            .contentType(ContentType.JSON)
            .body(NewFile(null, "title4", listOf("invalid")))
            .When()
            .post("/file")
            .then()
            .statusCode(400)

        given()
            .contentType(ContentType.JSON)
            .body(NewFile(null, "title4", emptyList(), listOf("invalid")))
            .When()
            .post("/file")
            .then()
            .statusCode(400)
    }

    @Test
    fun testGetFileReturnsNotFound() {
        get("/file/{id}", "invalid")
            .then()
            .statusCode(404)
    }

    @Test
    fun testGetSingleFile() {
        val file = get("/file/{id}", "e2")
            .then()
            .statusCode(200)
            .extract().to<File>()
        assertThat(file.id).isEqualTo("e2")
        assertThat(file.title).isEqualTo("title2")
        assertThat(file.dateCreated).isEqualTo(file.dateUpdated)
    }

    @Test
    fun testDeleteFile() {
        delete("/file/{id}", "e3")
            .then()
            .statusCode(200)
        get("file/{id}", "e3")
            .then()
            .statusCode(404)
    }

    @Test
    fun testDeleteFileReturnsNotFound() {
        delete("/file/{id}", "invalid")
            .then()
            .statusCode(404)
    }

    @Test
    fun testCannotDeleteNonFileEntry() {
        delete("/file/{id}", "e1")
            .then()
            .statusCode(404)
    }

    @Test
    fun testUpdateFile() {
        val updatedFile = NewFile("e2", "modified", listOf("t1"), listOf("c1"))
        val updated = given()
            .contentType(ContentType.JSON)
            .body(updatedFile)
            .When()
            .put("/file")
            .then()
            .statusCode(200)
            .extract().to<File>()
        assertThat(updated.title).isEqualTo("modified")
        assertThat(updated.tags).hasSize(1).extracting("id").containsExactly("t1")
        assertThat(updated.collections).hasSize(1).extracting("id").containsExactly("c1")
        assertThat(updated.dateCreated).isNotEqualTo(updated.dateUpdated)
        val retrieved = get("/file/{id}", "e2")
            .then().extract().to<File>()
        assertThat(retrieved).usingRecursiveComparison().ignoringFields("props", "dateUpdated").isEqualTo(updated)
    }

    @Test
    fun testCannotUpdateNonFile() {
        // e1 = existing file entry
        val updatedFile = NewFile("e1", "modified", emptyList())
        given()
            .contentType(ContentType.JSON)
            .body(updatedFile)
            .When()
            .put("/file")
            .then()
            .statusCode(404)
    }

    @Test
    fun testUpdateFileReturnsNotFound() {
        val updatedFile = NewFile("invalid", "modified", emptyList())
        given()
            .contentType(ContentType.JSON)
            .body(updatedFile)
            .When()
            .put("/file")
            .then()
            .statusCode(404)
    }

    @Test
    fun testGetAllFiles() {
        val files = given()
            .When()
            .get("/file")
            .then()
            .statusCode(200)
            .extract().to<Page<File>>()
        assertThat(files.page).isEqualTo(1)
        assertThat(files.total).isEqualTo(2)
        assertThat(files.content).hasSize(2).extracting("id").doesNotHaveDuplicates()
    }

    @Test
    fun testFilePaging() {
        val files = given()
            .queryParam("page", 2)
            .queryParam("size", 1)
            .When()
            .get("/file")
            .then()
            .statusCode(200)
            .extract().to<Page<File>>()
        // newest file first
        assertThat(files.page).isEqualTo(2)
        assertThat(files.size).isEqualTo(1)
        assertThat(files.total).isEqualTo(2)
        assertThat(files.content).hasSize(1).extracting("id").containsExactly("e2")
    }

    @Test
    fun testFileSorting() {
        val files = given()
            .queryParam("sort", "dateCreated")
            .queryParam("direction", "asc")
            .When()
            .get("/file")
            .then()
            .statusCode(200)
            .extract().to<Page<File>>()
        // oldest file first
        assertThat(files.total).isEqualTo(2)
        assertThat(files.content).hasSize(2).extracting("id").containsExactly("e2", "e3")
    }

    @Test
    fun testFileFiltering() {
        val created = given()
            .contentType(ContentType.JSON)
            .body(NewFile(null, "title4", emptyList(), listOf("c1")))
            .When()
            .post("/file")
            .then()
            .statusCode(201)
            .extract().to<File>()
        // filter by tags
        val filesTag = given()
            .queryParam("tags", "t1")
            .queryParam("direction", "asc")
            .When()
            .get("/file")
            .then()
            .statusCode(200)
            .extract().to<Page<File>>()
        assertThat(filesTag.total).isZero()
        assertThat(filesTag.content).isEmpty()

        // filter by collections
        val filesCollection = given()
            .queryParam("collections", "c1,c2")
            .queryParam("direction", "asc")
            .When()
            .get("/file")
            .then()
            .statusCode(200)
            .extract().to<Page<File>>()
        assertThat(filesCollection.total).isEqualTo(1)
        assertThat(filesCollection.content).hasSize(1).extracting("id").containsExactly(created.id)

        // filter by source
        val filesSource = given()
            .queryParam("source", "me")
            .queryParam("direction", "asc")
            .When()
            .get("/file")
            .then()
            .statusCode(200)
            .extract().to<Page<File>>()
        assertThat(filesSource.total).isEqualTo(1)
        assertThat(filesSource.content).hasSize(1).extracting("id").containsExactly(created.id)
    }

    @Test
    fun testGetInvalidVersion() {
        get("/file/{id}/{version}", "e2", 2)
            .then()
            .statusCode(404)
    }

    @Test
    fun testGetVersion() {
        val newFile = NewFile(null, "title4", emptyList())
        val created = given()
            .contentType(ContentType.JSON)
            .body(newFile)
            .When()
            .post("/file")
            .then()
            .statusCode(201)
            .extract().to<File>()

        assertThat(created.version).isOne()
        assertThat(created.title).isEqualTo(newFile.title)
        assertThat(created.dateCreated).isEqualTo(created.dateUpdated)

        // update
        val updateFile = NewFile(created.id, "new content", emptyList())
        val updated = given()
            .contentType(ContentType.JSON)
            .body(updateFile)
            .When()
            .put("/file")
            .then()
            .statusCode(200)
            .extract().to<File>()

        assertThat(updated.title).isEqualTo(updated.title)
        assertThat(updated.version).isEqualTo(2)
        assertThat(updated.dateCreated).isNotEqualTo(updated.dateUpdated)

        // retrieve versions
        val original = get("/file/{id}/{version}", created.id, 1)
            .then()
            .statusCode(200)
            .extract().to<File>()
        assertThat(original.version).isOne()
        assertThat(original.title).isEqualTo(newFile.title)
        assertThat(original.dateCreated).isEqualTo(original.dateUpdated)

        val current = get("/file/{id}", created.id)
            .then()
            .statusCode(200)
            .extract().to<File>()
        assertThat(current.version).isEqualTo(2)
        assertThat(current.title).isEqualTo(updateFile.title)
        assertThat(current.dateCreated).isNotEqualTo(current.dateUpdated)
    }

    @Test
    fun testUpdateFileNoNewVersion() {
        val newFile = NewFile(null, "title5", emptyList())
        val created = given()
            .contentType(ContentType.JSON)
            .body(newFile)
            .When()
            .post("/file")
            .then()
            .statusCode(201)
            .extract().to<File>()

        assertThat(created.version).isOne()
        assertThat(created.title).isEqualTo(newFile.title)
        assertThat(created.dateCreated).isEqualTo(created.dateUpdated)

        // update no new version
        val updateFile = NewFile(created.id, "new title", emptyList())
        val updated = given()
            .contentType(ContentType.JSON)
            .body(updateFile)
            .When()
            .put("/file?newVersion=false")
            .then()
            .statusCode(200)
            .extract().to<File>()

        assertThat(updated.title).isEqualTo(updateFile.title)
        assertThat(updated.version).isEqualTo(1)
        assertThat(updated.dateCreated).isNotEqualTo(updated.dateUpdated)

        // retrieve latest version
        val current = get("/file/{id}", created.id)
            .then()
            .statusCode(200)
            .extract().to<File>()
        assertThat(current.version).isEqualTo(1)
        assertThat(current.title).isEqualTo(updateFile.title)
        assertThat(current.dateCreated).isNotEqualTo(current.dateUpdated)
    }


}
