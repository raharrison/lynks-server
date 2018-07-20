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
    fun testCannotDeleteNonLinkEntry() {
        delete("/link/{id}", "e2")
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
    fun testCannotUpdateNonLink() {
        // e2 = existing note entry
        val updatedLink = NewLink("e2", "title2", "modified", emptyList())
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

    @Test
    fun testGetInvalidVersion() {
        get("/link/{id}/{version}", "e1", 2)
                .then()
                .statusCode(404)
    }

    @Test
    fun testGetVersion() {
        val newLink = NewLink(null, "title4", "content4", emptyList())
        val created = given()
                .contentType(ContentType.JSON)
                .body(newLink)
                .When()
                .post("/link")
                .then()
                .statusCode(201)
                .extract().to<Link>()

        assertThat(created.version).isZero()
        assertThat(created.title).isEqualTo(newLink.title)
        assertThat(created.url).isEqualTo(newLink.url)

        // update
        val updateLink = NewLink(created.id, "edited", "google.com", emptyList())
        val updated = given()
                .contentType(ContentType.JSON)
                .body(updateLink)
                .When()
                .put("/link")
                .then()
                .statusCode(200)
                .extract().to<Link>()

        assertThat(updated.title).isEqualTo(updateLink.title)
        assertThat(updated.url).isEqualTo(updateLink.url)
        assertThat(updated.version).isOne()

        // retrieve versions
        val original = get("/link/{id}/{version}", created.id, 0)
                .then()
                .statusCode(200)
                .extract().to<Link>()
        assertThat(original.version).isEqualTo(0)
        assertThat(original.title).isEqualTo(newLink.title)
        assertThat(original.url).isEqualTo(newLink.url)

        val current = get("/link/{id}/{version}", created.id, 1)
                .then()
                .statusCode(200)
                .extract().to<Link>()
        assertThat(current.version).isEqualTo(1)
        assertThat(current.title).isEqualTo(updateLink.title)
        assertThat(current.url).isEqualTo(updateLink.url)
    }

    @Test
    fun testSetReadInvalidLink() {
        post("/link/{id}/read", "invalid")
                .then()
                .statusCode(404)
        post("/link/{id}/unread", "invalid")
                .then()
                .statusCode(404)
    }

    @Test
    fun testSetLinkRead() {
        val read = post("/link/{id}/read", "e1")
                .then()
                .statusCode(200)
                .extract().to<Link>()
        assertThat(read.props.getAttribute("read")).isEqualTo(true)
        val retrieved = get("/link/{id}", "e1")
                .then()
                .statusCode(200)
                .extract().to<Link>()
        assertThat(retrieved.props.getAttribute("read")).isEqualTo(true)
    }

    @Test
    fun testSetLinkUnread() {
        val read = post("/link/{id}/unread", "e1")
                .then()
                .statusCode(200)
                .extract().to<Link>()
        assertThat(read.props.getAttribute("read")).isEqualTo(false)
        val retrieved = get("/link/{id}", "e1")
                .then()
                .statusCode(200)
                .extract().to<Link>()
        assertThat(retrieved.props.getAttribute("read")).isEqualTo(false)
    }

    // TODO: submit invalid url
    // TODO: link processing

}