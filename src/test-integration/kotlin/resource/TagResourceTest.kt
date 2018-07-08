package resource

import common.ServerTest
import io.restassured.RestAssured.*
import io.restassured.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tag.NewTag
import tag.Tag
import util.createDummyTag

class TagResourceTest : ServerTest() {

    @BeforeEach
    fun createTags() {
        /*
         t1                t2
                            |
                            |
                      ------+------
                     t3           t4
                      |            |
                      |            |
                -----+------       +----
               t5         t6           t7
               |
               |
           ----+
          t8
         */

        createDummyTag("t1", "tag1")
        createDummyTag("t2", "tag2")
        createDummyTag("t3", "tag3", "t2")
        createDummyTag("t4", "tag4", "t2")
        createDummyTag("t5", "tag5", "t3")
        createDummyTag("t6", "tag6", "t3")
        createDummyTag("t7", "tag7", "t4")
        createDummyTag("t8", "tag8", "t5")
        post("/tag/refresh")
    }

    @Test
    fun testGetAll() {
        val tags = get("/tag")
                .then()
                .extract().to<Collection<Tag>>()
        assertThat(tags).isNotEmpty
        assertThat(tags).extracting("id").contains("t1", "t2")
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
        assertThat(tag1.children).isEmpty()

        val tag2 = get("/tag/{id}", "t2")
                .then()
                .statusCode(200)
                .extract().to<Tag>()
        assertThat(tag2.id).isEqualTo("t2")
        assertThat(tag2.name).isEqualTo("tag2")
        assertThat(tag2.children).hasSize(2).extracting("id").containsExactlyInAnyOrder("t3", "t4")
    }

    @Test
    fun testDeleteTagReturnsNotFound() {
        delete("/tag/{id}", "invalid")
                .then()
                .statusCode(404)
    }

    @Test
    fun testDeleteTags() {
        delete("/tag/{id}", "t5")
                .then()
                .statusCode(200)
        get("/tag/{id}", "t5")
                .then()
                .statusCode(404)
        get("/tag/{id}", "t8")
                .then()
                .statusCode(404)
        val t3 = get("/tag/{id}", "t3")
                .then()
                .statusCode(200)
                .extract().to<Tag>()
        assertThat(t3.children).hasSize(1).extracting("id").doesNotContain("t5")
    }

    @Test
    fun testCreateTag() {
        val newTag = NewTag(null,"tag10", null)
        val created = given()
                .contentType(ContentType.JSON)
                .body(newTag)
                .When()
                .post("/tag")
                .then()
                .statusCode(201)
                .extract().to<Tag>()
        assertThat(created.name).isEqualTo("tag10")
        assertThat(created.children).isEmpty()
        val retrieved = get("/tag/{id}", created.id)
                .then()
                .extract().to<Tag>()
        assertThat(created).isEqualTo(retrieved)
    }

    @Test
    fun testUpdateTag() {
        val updatedTag = NewTag("t7","updated", null)
        val updated = given()
                .contentType(ContentType.JSON)
                .body(updatedTag)
                .When()
                .put("/tag")
                .then()
                .statusCode(200)
                .extract().to<Tag>()
        assertThat(updated.id).isEqualTo("t7")
        assertThat(updated.name).isEqualTo("updated")
        assertThat(updated.children).isEmpty()
        val retrieved = get("/tag/{id}", updated.id)
                .then()
                .extract().to<Tag>()
        assertThat(updated).isEqualTo(retrieved)
    }

    @Test
    fun testUpdateTagNoRow() {
        val updatedTag = NewTag("invalid","updated", null)
        given()
                .contentType(ContentType.JSON)
                .body(updatedTag)
                .When()
                .put("/tag")
                .then()
                .statusCode(404)
    }

}