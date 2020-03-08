package resource

import common.ServerTest
import group.NewTag
import group.Tag
import io.restassured.RestAssured.*
import io.restassured.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import util.createDummyTag

class TagResourceTest : ServerTest() {

    @BeforeEach
    fun createTags() {
        createDummyTag("t1", "tag1")
        createDummyTag("t2", "tag2")
        createDummyTag("t3", "tag3")
        createDummyTag("t4", "tag4")
        post("/tag/refresh")
    }

    @Test
    fun testGetAll() {
        val tags = get("/tag")
                .then()
                .extract().to<Collection<Tag>>()
        assertThat(tags).isNotEmpty
        assertThat(tags).extracting("id").contains("t1", "t2", "t3")
    }

    @Test
    fun testGetTagReturnsNotFound() {
        get("/tag/{id}", "invalid")
                .then()
                .statusCode(404)
    }

    @Test
    fun testGetTagById() {
        val tag1 = get("/tag/{id}", "t1")
                .then()
                .statusCode(200)
                .extract().to<Tag>()
        assertThat(tag1.id).isEqualTo("t1")
        assertThat(tag1.name).isEqualTo("tag1")
    }

    @Test
    fun testDeleteTagReturnsNotFound() {
        delete("/tag/{id}", "invalid")
                .then()
                .statusCode(404)
    }

    @Test
    fun testDeleteTags() {
        delete("/tag/{id}", "t4")
                .then()
                .statusCode(200)
        get("/tag/{id}", "t4")
                .then()
                .statusCode(404)
    }

    @Test
    fun testCreateTag() {
        val newTag = NewTag(null, "tag10")
        val created = given()
                .contentType(ContentType.JSON)
                .body(newTag)
                .When()
                .post("/tag")
                .then()
                .statusCode(201)
                .extract().to<Tag>()
        assertThat(created.name).isEqualTo("tag10")
        assertThat(created.dateCreated).isEqualTo(created.dateUpdated)
        val retrieved = get("/tag/{id}", created.id)
                .then()
                .extract().to<Tag>()
        assertThat(created).isEqualTo(retrieved)
    }

    @Test
    fun testUpdateTag() {
        val updatedTag = NewTag("t2", "updated")
        val updated = given()
                .contentType(ContentType.JSON)
                .body(updatedTag)
                .When()
                .put("/tag")
                .then()
                .statusCode(200)
                .extract().to<Tag>()
        assertThat(updated.id).isEqualTo("t2")
        assertThat(updated.name).isEqualTo("updated")
        assertThat(updated.dateUpdated).isNotEqualTo(updated.dateCreated)
        val retrieved = get("/tag/{id}", updated.id)
                .then()
                .extract().to<Tag>()
        assertThat(updated).isEqualTo(retrieved)
    }

    @Test
    fun testUpdateTagNoRow() {
        val updatedTag = NewTag("invalid", "updated")
        given()
                .contentType(ContentType.JSON)
                .body(updatedTag)
                .When()
                .put("/tag")
                .then()
                .statusCode(404)
    }

}