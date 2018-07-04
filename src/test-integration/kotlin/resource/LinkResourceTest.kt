package resource

import common.EntryType
import common.Link
import common.NewLink
import common.ServerTest
import io.restassured.RestAssured.*
import io.restassured.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import util.createDummyEntry
import util.createDummyTag

class LinkResourceTest: ServerTest() {

    @BeforeEach
    fun createEntries() {
        createDummyEntry("e1", "title1", "content1", EntryType.LINK)
        Thread.sleep(10)// prevent having same creation timestamp
        createDummyEntry("e2", "title2", "content2", EntryType.NOTE)
        Thread.sleep(10)
        createDummyEntry("e3", "title3", "content3", EntryType.LINK)
        createDummyTag("t1", "tag1")
        post("/tag/refresh")
    }

    @Test
    fun testCreateLinkNoProcess() {
        val newLink = NewLink(null, "title4", "http://google.com/page", listOf("t1"), false)
        val created = given()
                .contentType(ContentType.JSON)
                .body(newLink)
                .When()
                .post("/link")
                .then()
                .statusCode(201)
                .extract().to<Link>()
        assertThat(created.title).isEqualTo(newLink.title)
        assertThat(created.url).isEqualTo(newLink.url)
        assertThat(created.source).isEqualTo("google.com")
        assertThat(created.type).isEqualTo(EntryType.LINK)
        assertThat(created.tags).hasSize(1).extracting("id").containsExactly("t1")
        assertThat(created.content).isNull()
        val retrieved = get("/link/{id}", created.id)
                .then()
                .extract().to<Link>()
        assertThat(created).isEqualToIgnoringGivenFields(retrieved, "props")
    }

    @Test
    fun testGetLinkReturnsNotFound() {
        get("/link/{id}", "invalid")
                .then()
                .statusCode(404)
    }

    @Test
    fun testGetSingleLink() {
        val link = get("/link/{id}", "e3")
                .then()
                .statusCode(200)
                .extract().to<Link>()
        assertThat(link.id).isEqualTo("e3")
        assertThat(link.title).isEqualTo("title3")
        assertThat(link.url).isEqualTo("content3")
    }

    @Test
    fun testDeleteLink() {
        delete("/link/{id}", "e3")
                .then()
                .statusCode(200)
        get("link/{id}", "e3")
                .then()
                .statusCode(404)
    }

    @Test
    fun testDeleteLinkReturnsNotFound() {
        delete("/link/{id}", "invalid")
                .then()
                .statusCode(404)
    }

    @Test
    fun testUpdateLinkNoProcess() {
        val updatedLink = NewLink("e3", "title3", "http://gmail.com", listOf("t1"), false)
        val updated = given()
                .contentType(ContentType.JSON)
                .body(updatedLink)
                .When()
                .put("/link")
                .then()
                .statusCode(200)
                .extract().to<Link>()
        assertThat(updated.url).isEqualTo(updatedLink.url)
        assertThat(updated.source).isEqualTo("gmail.com")
        assertThat(updated.tags).hasSize(1).extracting("id").containsExactly("t1")
        val retrieved = get("/link/{id}", "e3")
                .then().extract().to<Link>()
        assertThat(retrieved).isEqualToIgnoringGivenFields(updated, "dateUpdated", "props")
    }

    @Test
    fun testUpdateLinkReturnsNotFound() {
        val updatedLink = NewLink("invalid", "title2", "modified", emptyList())
        given()
                .contentType(ContentType.JSON)
                .body(updatedLink)
                .When()
                .put("/link")
                .then()
                .statusCode(404)
    }

    @Test
    fun testGetAllLinks() {
        val links = given()
                .When()
                .get("/link")
                .then()
                .statusCode(200)
                .extract().to<List<Link>>()
        assertThat(links).hasSize(2).extracting("id").doesNotHaveDuplicates()
    }

    @Test
    fun testLinkPaging() {
        val links = given()
                .queryParam("offset", 1)
                .queryParam("limit", 1)
                .When()
                .get("/link")
                .then()
                .statusCode(200)
                .extract().to<List<Link>>()
        // newest link first
        assertThat(links).hasSize(1).extracting("id").containsExactly("e1")
    }

    // TODO: delete note from link endpoint
    // TODO: update note from link endpoint
    // TODO: submit invalid url
    // TODO: link processing

}