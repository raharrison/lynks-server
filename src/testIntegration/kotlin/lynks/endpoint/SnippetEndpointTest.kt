package lynks.endpoint

import io.restassured.RestAssured.*
import io.restassured.http.ContentType
import lynks.common.EntryType
import lynks.common.NewSnippet
import lynks.common.ServerTest
import lynks.common.Snippet
import lynks.common.page.Page
import lynks.util.createDummyCollection
import lynks.util.createDummyEntry
import lynks.util.createDummyTag
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SnippetEndpointTest: ServerTest() {

    @BeforeEach
    fun createEntries() {
        createDummyEntry("e1", "title1", "content1", EntryType.LINK)
        Thread.sleep(10)// prevent having same creation timestamp
        createDummyEntry("e2", "title2", "content2", EntryType.SNIPPET)
        Thread.sleep(10)
        createDummyEntry("e3", "title3", "content3", EntryType.SNIPPET)
        createDummyTag("t1", "tag1")
        createDummyCollection("c1", "col1")
        post("/tag/refresh")
        post("/collection/refresh")
    }

    @Test
    fun testCreateSnippet() {
        val newSnippet = NewSnippet(null,  "content4", listOf("t1"), listOf("c1"))
        val created = given()
                .contentType(ContentType.JSON)
                .body(newSnippet)
                .When()
                .post("/snippet")
                .then()
                .statusCode(201)
                .extract().to<Snippet>()
        assertThat(created.plainText).isEqualTo(newSnippet.plainText)
        assertThat(created.markdownText).isEqualTo("<p>content4</p>\n")
        assertThat(created.type).isEqualTo(EntryType.SNIPPET)
        assertThat(created.tags).hasSize(1).extracting("id").containsExactly("t1")
        assertThat(created.collections).hasSize(1).extracting("id").containsExactly("c1")
        assertThat(created.dateCreated).isEqualTo(created.dateUpdated)
        val retrieved = get("/snippet/{id}", created.id)
                .then()
                .extract().to<Snippet>()
        assertThat(created).usingRecursiveComparison().ignoringFields("props").isEqualTo(retrieved)
    }

    @Test
    fun testCreateSnippetWithInvalidGroups() {
        given()
            .contentType(ContentType.JSON)
            .body(NewSnippet(null,  "content4", listOf("invalid")))
            .When()
            .post("/snippet")
            .then()
            .statusCode(400)

        given()
            .contentType(ContentType.JSON)
            .body(NewSnippet(null, "content4", emptyList(), listOf("invalid")))
            .When()
            .post("/snippet")
            .then()
            .statusCode(400)
    }

    @Test
    fun testGetSnippetReturnsNotFound() {
        get("/snippet/{id}", "invalid")
                .then()
                .statusCode(404)
    }

    @Test
    fun testGetSingleSnippet() {
        val snippet = get("/snippet/{id}", "e2")
                .then()
                .statusCode(200)
                .extract().to<Snippet>()
        assertThat(snippet.id).isEqualTo("e2")
        assertThat(snippet.plainText).isEqualTo("content2")
        assertThat(snippet.dateCreated).isEqualTo(snippet.dateUpdated)
    }

    @Test
    fun testDeleteSnippet() {
        delete("/snippet/{id}", "e3")
                .then()
                .statusCode(200)
        get("snippet/{id}", "e3")
                .then()
                .statusCode(404)
    }

    @Test
    fun testDeleteSnippetReturnsNotFound() {
        delete("/snippet/{id}", "invalid")
                .then()
                .statusCode(404)
    }

    @Test
    fun testCannotDeleteNonSnippetEntry() {
        delete("/snippet/{id}", "e1")
                .then()
                .statusCode(404)
    }

    @Test
    fun testUpdateSnippet() {
        val updatedSnippet = NewSnippet("e2", "modified", listOf("t1"), listOf("c1"))
        val updated = given()
                .contentType(ContentType.JSON)
                .body(updatedSnippet)
                .When()
                .put("/snippet")
                .then()
                .statusCode(200)
                .extract().to<Snippet>()
        assertThat(updated.plainText).isEqualTo("modified")
        assertThat(updated.markdownText).isEqualTo("<p>modified</p>\n")
        assertThat(updated.tags).hasSize(1).extracting("id").containsExactly("t1")
        assertThat(updated.collections).hasSize(1).extracting("id").containsExactly("c1")
        assertThat(updated.dateCreated).isNotEqualTo(updated.dateUpdated)
        val retrieved = get("/snippet/{id}", "e2")
                .then().extract().to<Snippet>()
        assertThat(retrieved).usingRecursiveComparison().ignoringFields("props", "dateUpdated").isEqualTo(updated)
    }

    @Test
    fun testCannotUpdateNonSnippet() {
        // e1 = existing snippet entry
        val updatedSnippet = NewSnippet("e1", "modified", emptyList())
        given()
                .contentType(ContentType.JSON)
                .body(updatedSnippet)
                .When()
                .put("/snippet")
                .then()
                .statusCode(404)
    }

    @Test
    fun testUpdateSnippetReturnsNotFound() {
        val updatedSnippet = NewSnippet("invalid", "modified", emptyList())
        given()
                .contentType(ContentType.JSON)
                .body(updatedSnippet)
                .When()
                .put("/snippet")
                .then()
                .statusCode(404)
    }

    @Test
    fun testGetAllSnippets() {
        val snippets = given()
                .When()
                .get("/snippet")
                .then()
                .statusCode(200)
                .extract().to<Page<Snippet>>()
        assertThat(snippets.page).isEqualTo(1)
        assertThat(snippets.total).isEqualTo(2)
        assertThat(snippets.content).hasSize(2).extracting("id").doesNotHaveDuplicates()
    }

    @Test
    fun testSnippetPaging() {
        val snippets = given()
                .queryParam("page", 2)
                .queryParam("size", 1)
                .When()
                .get("/snippet")
                .then()
                .statusCode(200)
                .extract().to<Page<Snippet>>()
        // newest snippet first
        assertThat(snippets.page).isEqualTo(2)
        assertThat(snippets.size).isEqualTo(1)
        assertThat(snippets.total).isEqualTo(2)
        assertThat(snippets.content).hasSize(1).extracting("id").containsExactly("e2")
    }

    @Test
    fun testSnippetSorting() {
        val snippets = given()
            .queryParam("sort", "dateCreated")
            .queryParam("direction", "asc")
            .When()
            .get("/snippet")
            .then()
            .statusCode(200)
            .extract().to<Page<Snippet>>()
        // oldest snippet first
        assertThat(snippets.total).isEqualTo(2)
        assertThat(snippets.content).hasSize(2).extracting("id").containsExactly("e2", "e3")
    }

    @Test
    fun testSnippetFiltering() {
        val created = given()
            .contentType(ContentType.JSON)
            .body(NewSnippet(null,  "content4", emptyList(), listOf("c1")))
            .When()
            .post("/snippet")
            .then()
            .statusCode(201)
            .extract().to<Snippet>()
        // filter by tags
        val snippetsTag = given()
            .queryParam("tags", "t1")
            .queryParam("direction", "asc")
            .When()
            .get("/snippet")
            .then()
            .statusCode(200)
            .extract().to<Page<Snippet>>()
        assertThat(snippetsTag.total).isZero()
        assertThat(snippetsTag.content).isEmpty()

        // filter by collections
        val snippetsCollection = given()
            .queryParam("collections", "c1,c2")
            .queryParam("direction", "asc")
            .When()
            .get("/snippet")
            .then()
            .statusCode(200)
            .extract().to<Page<Snippet>>()
        assertThat(snippetsCollection.total).isEqualTo(1)
        assertThat(snippetsCollection.content).hasSize(1).extracting("id").containsExactly(created.id)

        // filter by source
        val snippetsSource = given()
            .queryParam("source", "me")
            .queryParam("direction", "asc")
            .When()
            .get("/snippet")
            .then()
            .statusCode(200)
            .extract().to<Page<Snippet>>()
        assertThat(snippetsSource.total).isEqualTo(1)
        assertThat(snippetsSource.content).hasSize(1).extracting("id").containsExactly(created.id)
    }

    @Test
    fun testGetInvalidVersion() {
        get("/snippet/{id}/{version}", "e2", 2)
                .then()
                .statusCode(404)
    }

    @Test
    fun testGetVersion() {
        val newSnippet = NewSnippet(null, "content4", emptyList())
        val created = given()
                .contentType(ContentType.JSON)
                .body(newSnippet)
                .When()
                .post("/snippet")
                .then()
                .statusCode(201)
                .extract().to<Snippet>()

        assertThat(created.version).isOne()
        assertThat(created.plainText).isEqualTo(newSnippet.plainText)
        assertThat(created.dateCreated).isEqualTo(created.dateUpdated)

        // update
        val updateSnippet = NewSnippet(created.id, "new content", emptyList())
        val updated = given()
                .contentType(ContentType.JSON)
                .body(updateSnippet)
                .When()
                .put("/snippet")
                .then()
                .statusCode(200)
                .extract().to<Snippet>()

        assertThat(updated.plainText).isEqualTo(updated.plainText)
        assertThat(updated.version).isEqualTo(2)
        assertThat(updated.dateCreated).isNotEqualTo(updated.dateUpdated)

        // retrieve versions
        val original = get("/snippet/{id}/{version}", created.id, 1)
                .then()
                .statusCode(200)
                .extract().to<Snippet>()
        assertThat(original.version).isOne()
        assertThat(original.plainText).isEqualTo(newSnippet.plainText)
        assertThat(original.dateCreated).isEqualTo(original.dateUpdated)

        val current = get("/snippet/{id}", created.id)
                .then()
                .statusCode(200)
                .extract().to<Snippet>()
        assertThat(current.version).isEqualTo(2)
        assertThat(current.plainText).isEqualTo(updateSnippet.plainText)
        assertThat(current.dateCreated).isNotEqualTo(current.dateUpdated)
    }

    @Test
    fun testUpdateSnippetNoNewVersion() {
        val newSnippet = NewSnippet(null, "content5", emptyList())
        val created = given()
            .contentType(ContentType.JSON)
            .body(newSnippet)
            .When()
            .post("/snippet")
            .then()
            .statusCode(201)
            .extract().to<Snippet>()

        assertThat(created.version).isOne()
        assertThat(created.plainText).isEqualTo(newSnippet.plainText)
        assertThat(created.dateCreated).isEqualTo(created.dateUpdated)

        // update no new version
        val updateSnippet = NewSnippet(created.id, "new content", emptyList())
        val updated = given()
            .contentType(ContentType.JSON)
            .body(updateSnippet)
            .When()
            .put("/snippet?newVersion=false")
            .then()
            .statusCode(200)
            .extract().to<Snippet>()

        assertThat(updated.plainText).isEqualTo(updateSnippet.plainText)
        assertThat(updated.version).isEqualTo(1)
        assertThat(updated.dateCreated).isNotEqualTo(updated.dateUpdated)

        // retrieve latest version
        val current = get("/snippet/{id}", created.id)
            .then()
            .statusCode(200)
            .extract().to<Snippet>()
        assertThat(current.version).isEqualTo(1)
        assertThat(current.plainText).isEqualTo(updateSnippet.plainText)
        assertThat(current.dateCreated).isNotEqualTo(current.dateUpdated)
    }


}
