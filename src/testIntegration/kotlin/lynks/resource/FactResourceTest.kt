package lynks.resource

import io.restassured.RestAssured.*
import io.restassured.http.ContentType
import lynks.common.EntryType
import lynks.common.Fact
import lynks.common.NewFact
import lynks.common.ServerTest
import lynks.common.page.Page
import lynks.util.createDummyCollection
import lynks.util.createDummyEntry
import lynks.util.createDummyTag
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FactResourceTest: ServerTest() {

    @BeforeEach
    fun createEntries() {
        createDummyEntry("e1", "title1", "content1", EntryType.LINK)
        Thread.sleep(10)// prevent having same creation timestamp
        createDummyEntry("e2", "title2", "content2", EntryType.FACT)
        Thread.sleep(10)
        createDummyEntry("e3", "title3", "content3", EntryType.FACT)
        createDummyTag("t1", "tag1")
        createDummyCollection("c1", "col1")
        post("/tag/refresh")
        post("/collection/refresh")
    }

    @Test
    fun testCreateFact() {
        val newFact = NewFact(null,  "content4", listOf("t1"), listOf("c1"))
        val created = given()
                .contentType(ContentType.JSON)
                .body(newFact)
                .When()
                .post("/fact")
                .then()
                .statusCode(201)
                .extract().to<Fact>()
        assertThat(created.plainText).isEqualTo(newFact.plainText)
        assertThat(created.markdownText).isEqualTo("<p>content4</p>\n")
        assertThat(created.type).isEqualTo(EntryType.FACT)
        assertThat(created.tags).hasSize(1).extracting("id").containsExactly("t1")
        assertThat(created.collections).hasSize(1).extracting("id").containsExactly("c1")
        assertThat(created.dateCreated).isEqualTo(created.dateUpdated)
        val retrieved = get("/fact/{id}", created.id)
                .then()
                .extract().to<Fact>()
        assertThat(created).usingRecursiveComparison().ignoringFields("props").isEqualTo(retrieved)
    }

    @Test
    fun testCreateFactWithInvalidGroups() {
        given()
            .contentType(ContentType.JSON)
            .body(NewFact(null,  "content4", listOf("invalid")))
            .When()
            .post("/fact")
            .then()
            .statusCode(400)

        given()
            .contentType(ContentType.JSON)
            .body(NewFact(null, "content4", emptyList(), listOf("invalid")))
            .When()
            .post("/fact")
            .then()
            .statusCode(400)
    }

    @Test
    fun testGetFactReturnsNotFound() {
        get("/fact/{id}", "invalid")
                .then()
                .statusCode(404)
    }

    @Test
    fun testGetSingleFact() {
        val fact = get("/fact/{id}", "e2")
                .then()
                .statusCode(200)
                .extract().to<Fact>()
        assertThat(fact.id).isEqualTo("e2")
        assertThat(fact.plainText).isEqualTo("content2")
        assertThat(fact.dateCreated).isEqualTo(fact.dateUpdated)
    }

    @Test
    fun testDeleteFact() {
        delete("/fact/{id}", "e3")
                .then()
                .statusCode(200)
        get("fact/{id}", "e3")
                .then()
                .statusCode(404)
    }

    @Test
    fun testDeleteFactReturnsNotFound() {
        delete("/fact/{id}", "invalid")
                .then()
                .statusCode(404)
    }

    @Test
    fun testCannotDeleteNonFactEntry() {
        delete("/fact/{id}", "e1")
                .then()
                .statusCode(404)
    }

    @Test
    fun testUpdateFact() {
        val updatedFact = NewFact("e2", "modified", listOf("t1"), listOf("c1"))
        val updated = given()
                .contentType(ContentType.JSON)
                .body(updatedFact)
                .When()
                .put("/fact")
                .then()
                .statusCode(200)
                .extract().to<Fact>()
        assertThat(updated.plainText).isEqualTo("modified")
        assertThat(updated.markdownText).isEqualTo("<p>modified</p>\n")
        assertThat(updated.tags).hasSize(1).extracting("id").containsExactly("t1")
        assertThat(updated.collections).hasSize(1).extracting("id").containsExactly("c1")
        assertThat(updated.dateCreated).isNotEqualTo(updated.dateUpdated)
        val retrieved = get("/fact/{id}", "e2")
                .then().extract().to<Fact>()
        assertThat(retrieved).usingRecursiveComparison().ignoringFields("props", "dateUpdated").isEqualTo(updated)
    }

    @Test
    fun testCannotUpdateNonFact() {
        // e1 = existing link entry
        val updatedLink = NewFact("e1", "modified", emptyList())
        given()
                .contentType(ContentType.JSON)
                .body(updatedLink)
                .When()
                .put("/fact")
                .then()
                .statusCode(404)
    }

    @Test
    fun testUpdateFactReturnsNotFound() {
        val updatedFact = NewFact("invalid", "modified", emptyList())
        given()
                .contentType(ContentType.JSON)
                .body(updatedFact)
                .When()
                .put("/fact")
                .then()
                .statusCode(404)
    }

    @Test
    fun testGetAllFacts() {
        val facts = given()
                .When()
                .get("/fact")
                .then()
                .statusCode(200)
                .extract().to<Page<Fact>>()
        assertThat(facts.page).isEqualTo(1)
        assertThat(facts.total).isEqualTo(2)
        assertThat(facts.content).hasSize(2).extracting("id").doesNotHaveDuplicates()
    }

    @Test
    fun testFactPaging() {
        val facts = given()
                .queryParam("page", 2)
                .queryParam("size", 1)
                .When()
                .get("/fact")
                .then()
                .statusCode(200)
                .extract().to<Page<Fact>>()
        // newest fact first
        assertThat(facts.page).isEqualTo(2)
        assertThat(facts.size).isEqualTo(1)
        assertThat(facts.total).isEqualTo(2)
        assertThat(facts.content).hasSize(1).extracting("id").containsExactly("e2")
    }

    @Test
    fun testFactSorting() {
        val facts = given()
            .queryParam("sort", "dateCreated")
            .queryParam("direction", "asc")
            .When()
            .get("/fact")
            .then()
            .statusCode(200)
            .extract().to<Page<Fact>>()
        // oldest fact first
        assertThat(facts.total).isEqualTo(2)
        assertThat(facts.content).hasSize(2).extracting("id").containsExactly("e2", "e3")
    }

    @Test
    fun testGetInvalidVersion() {
        get("/fact/{id}/{version}", "e2", 2)
                .then()
                .statusCode(404)
    }

    @Test
    fun testGetVersion() {
        val newFact = NewFact(null, "content4", emptyList())
        val created = given()
                .contentType(ContentType.JSON)
                .body(newFact)
                .When()
                .post("/fact")
                .then()
                .statusCode(201)
                .extract().to<Fact>()

        assertThat(created.version).isOne()
        assertThat(created.plainText).isEqualTo(newFact.plainText)
        assertThat(created.dateCreated).isEqualTo(created.dateUpdated)

        // update
        val updateFact = NewFact(created.id, "new content", emptyList())
        val updated = given()
                .contentType(ContentType.JSON)
                .body(updateFact)
                .When()
                .put("/fact")
                .then()
                .statusCode(200)
                .extract().to<Fact>()

        assertThat(updated.plainText).isEqualTo(updated.plainText)
        assertThat(updated.version).isEqualTo(2)
        assertThat(updated.dateCreated).isNotEqualTo(updated.dateUpdated)

        // retrieve versions
        val original = get("/fact/{id}/{version}", created.id, 1)
                .then()
                .statusCode(200)
                .extract().to<Fact>()
        assertThat(original.version).isOne()
        assertThat(original.plainText).isEqualTo(newFact.plainText)
        assertThat(original.dateCreated).isEqualTo(original.dateUpdated)

        val current = get("/fact/{id}", created.id)
                .then()
                .statusCode(200)
                .extract().to<Fact>()
        assertThat(current.version).isEqualTo(2)
        assertThat(current.plainText).isEqualTo(updateFact.plainText)
        assertThat(current.dateCreated).isNotEqualTo(current.dateUpdated)
    }

    @Test
    fun testUpdateFactNoNewVersion() {
        val newFact = NewFact(null, "content5", emptyList())
        val created = given()
            .contentType(ContentType.JSON)
            .body(newFact)
            .When()
            .post("/fact")
            .then()
            .statusCode(201)
            .extract().to<Fact>()

        assertThat(created.version).isOne()
        assertThat(created.plainText).isEqualTo(newFact.plainText)
        assertThat(created.dateCreated).isEqualTo(created.dateUpdated)

        // update no new version
        val updateFact = NewFact(created.id, "new content", emptyList())
        val updated = given()
            .contentType(ContentType.JSON)
            .body(updateFact)
            .When()
            .put("/fact?newVersion=false")
            .then()
            .statusCode(200)
            .extract().to<Fact>()

        assertThat(updated.plainText).isEqualTo(updateFact.plainText)
        assertThat(updated.version).isEqualTo(1)
        assertThat(updated.dateCreated).isNotEqualTo(updated.dateUpdated)

        // retrieve latest version
        val current = get("/fact/{id}", created.id)
            .then()
            .statusCode(200)
            .extract().to<Fact>()
        assertThat(current.version).isEqualTo(1)
        assertThat(current.plainText).isEqualTo(updateFact.plainText)
        assertThat(current.dateCreated).isNotEqualTo(current.dateUpdated)
    }


}
