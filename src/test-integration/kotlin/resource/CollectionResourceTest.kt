package resource

import common.ServerTest
import group.Collection
import group.NewCollection
import io.restassured.RestAssured.*
import io.restassured.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import util.createDummyCollection

class CollectionResourceTest : ServerTest() {

    @BeforeEach
    fun createCollections() {
        /*
         c1                c2
                            |
                            |
                      ------+------
                     c3           c4
                      |            |
                      |            |
                -----+------       +----
               c5         c6           c7
               |
               |
           ----+
          c8
         */

        createDummyCollection("c1", "col1")
        createDummyCollection("c2", "col2")
        createDummyCollection("c3", "col3", "c2")
        createDummyCollection("c4", "col4", "c2")
        createDummyCollection("c5", "col5", "c3")
        createDummyCollection("c6", "col6", "c3")
        createDummyCollection("c7", "col7", "c4")
        createDummyCollection("c8", "col8", "c5")
    }

    @Test
    fun testGetAll() {
        val collections = get("/collection")
                .then()
                .extract().to<List<Collection>>()
        assertThat(collections).isNotEmpty
        assertThat(collections).extracting("id").contains("c1", "c2")
    }

    @Test
    fun testGetCollectionReturnsNotFound() {
        get("/collection/{id}", "invalid")
                .then()
                .statusCode(404)
    }

    @Test
    fun testGetCollectionById() {
        val col1 = get("/collection/{id}", "c1")
                .then()
                .statusCode(200)
                .extract().to<Collection>()
        assertThat(col1.id).isEqualTo("c1")
        assertThat(col1.name).isEqualTo("col1")
        assertThat(col1.children).isEmpty()

        val col2 = get("/collection/{id}", "c2")
                .then()
                .statusCode(200)
                .extract().to<Collection>()
        assertThat(col2.id).isEqualTo("c2")
        assertThat(col2.name).isEqualTo("col2")
        assertThat(col2.children).hasSize(2).extracting("id").containsExactlyInAnyOrder("c3", "c4")
    }

    @Test
    fun testDeleteCollectionReturnsNotFound() {
        delete("/collection/{id}", "invalid")
                .then()
                .statusCode(404)
    }

    @Test
    fun testDeleteCollections() {
        delete("/collection/{id}", "c5")
                .then()
                .statusCode(200)
        get("/collection/{id}", "c5")
                .then()
                .statusCode(404)
        get("/collection/{id}", "c8")
                .then()
                .statusCode(404)
        val c3 = get("/collection/{id}", "c3")
                .then()
                .statusCode(200)
                .extract().to<Collection>()
        assertThat(c3.children).hasSize(1).extracting("id").doesNotContain("c5")
    }

    @Test
    fun testCreateCollection() {
        val newCollection = NewCollection(null, "col10", null)
        val created = given()
                .contentType(ContentType.JSON)
                .body(newCollection)
                .When()
                .post("/collection")
                .then()
                .statusCode(201)
                .extract().to<Collection>()
        assertThat(created.name).isEqualTo("col10")
        assertThat(created.children).isEmpty()
        assertThat(created.dateCreated).isEqualTo(created.dateUpdated)
        val retrieved = get("/collection/{id}", created.id)
                .then()
                .extract().to<Collection>()
        assertThat(created).isEqualTo(retrieved)
    }

    @Test
    fun testUpdateCollection() {
        val updatedCollection = NewCollection("c7", "updated", null)
        Thread.sleep(10)
        val updated = given()
                .contentType(ContentType.JSON)
                .body(updatedCollection)
                .When()
                .put("/collection")
                .then()
                .statusCode(200)
                .extract().to<Collection>()
        assertThat(updated.id).isEqualTo("c7")
        assertThat(updated.name).isEqualTo("updated")
        assertThat(updated.children).isEmpty()
        assertThat(updated.dateUpdated).isNotEqualTo(updated.dateCreated)
        val retrieved = get("/collection/{id}", updated.id)
                .then()
                .extract().to<Collection>()
        assertThat(updated).isEqualTo(retrieved)
    }

    @Test
    fun testUpdateCollectionNoRow() {
        val updatedCollection = NewCollection("invalid", "updated")
        given()
                .contentType(ContentType.JSON)
                .body(updatedCollection)
                .When()
                .put("/collection")
                .then()
                .statusCode(404)
    }

}