package resource

import comment.Comment
import comment.NewComment
import common.EntryType
import common.ServerTest
import io.restassured.RestAssured.*
import io.restassured.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import util.createDummyComment
import util.createDummyEntry

class CommentResourceTest: ServerTest() {

    @BeforeEach
    fun createEntries() {
        createDummyEntry("e1", "title1", "content1", EntryType.LINK)
        Thread.sleep(10)// prevent having same creation timestamp
        createDummyEntry("e2", "title2", "content2", EntryType.NOTE)
        createDummyComment("c1", "e1", "comment content")
        createDummyComment("c2", "e2", "comment content2")
        createDummyComment("c3", "e1", "comment content3")
    }

    @Test
    fun testCreateComment() {
        val newComment = NewComment(null, "some markdown text")
        val created = given()
                .contentType(ContentType.JSON)
                .body(newComment)
                .When()
                .post("/entry/{entryId}/comments", "e1")
                .then()
                .statusCode(201)
                .extract().to<Comment>()
        assertThat(created.entryId).isEqualTo("e1")
        assertThat(created.plainText).isEqualTo(newComment.plainText)
        assertThat(created.markdownText).isEqualTo("<p>some markdown text</p>\n")
        assertThat(created.entryId).isEqualTo("e1")
        val retrieved = get("/entry/{entryId}/comments/{id}", created.entryId, created.id)
                .then()
                .extract().to<Comment>()
        assertThat(created).isEqualTo(retrieved)
    }

    @Test
    fun testGetCommentReturnsNotFound() {
        get("/entry/{entryId}/comments/{id}", "invalid", "invalid")
                .then()
                .statusCode(404)
    }

    @Test
    fun testGetSingleComment() {
        val comment = get("/entry/{entryId}/comments/{id}", "e2", "c2")
                .then()
                .statusCode(200)
                .extract().to<Comment>()
        assertThat(comment.id).isEqualTo("c2")
        assertThat(comment.entryId).isEqualTo("e2")
        assertThat(comment.plainText).isEqualTo("comment content2")
    }

    @Test
    fun testCannotGetCommentFromOtherEntry() {
        get("/entry/{entryId}/comments/{id}", "e2", "c1")
                .then()
                .statusCode(404)
    }

    @Test
    fun testDeleteComment() {
        delete("/entry/{entryId}/comments/{id}", "e1", "c1")
                .then()
                .statusCode(200)
        get("/entry/{entryId}/comments/{id}", "e1", "c1")
                .then()
                .statusCode(404)
    }

    @Test
    fun testCannotDeleteCommentFromOtherEntry() {
        delete("/entry/{entryId}/comments/{id}", "e2", "c1")
                .then()
                .statusCode(404)
    }

    @Test
    fun testUpdateComment() {
        val updatedComment = NewComment("c2", "modified")
        val updated = given()
                .contentType(ContentType.JSON)
                .body(updatedComment)
                .When()
                .put("/entry/{entryId}/comments", "e2")
                .then()
                .statusCode(200)
                .extract().to<Comment>()
        assertThat(updated.plainText).isEqualTo("modified")
        assertThat(updated.markdownText).isEqualTo("<p>modified</p>\n")
        val retrieved = get("/entry/{entryId}/comments/{id}", "e2", "c2")
                .then().extract().to<Comment>()
        assertThat(retrieved).isEqualTo(updated)
    }

    @Test
    fun testUpdateCommentReturnsNotFound() {
        val updatedComment = NewComment("c2", "modified")
        given()
                .contentType(ContentType.JSON)
                .body(updatedComment)
                .When()
                .put("/entry/{entryId}/comments", "e3")
                .then()
                .statusCode(404)
    }

    @Test
    fun testCommentsForEntry() {
        val comments = get("/entry/{entryId}/comments", "e1")
                .then()
                .statusCode(200)
                .extract().to<List<Comment>>()
        assertThat(comments).hasSize(2).extracting("id").containsExactlyInAnyOrder("c1", "c3")
    }

    @Test
    fun testCommentPaging() {
        val comments = given()
                .queryParam("offset", 1)
                .queryParam("limit", 1)
                .When()
                .get("/entry/{entryId}/comments", "e1")
                .then()
                .statusCode(200)
                .extract().to<List<Comment>>()
        // newest comment first
        assertThat(comments).hasSize(1).extracting("id").containsExactly("c1")
    }


}