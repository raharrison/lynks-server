package lynks.endpoint

import io.restassured.RestAssured.*
import io.restassured.http.ContentType
import lynks.comment.Comment
import lynks.comment.NewComment
import lynks.common.CommentId
import lynks.common.EntryId
import lynks.common.EntryType
import lynks.common.ServerTest
import lynks.common.page.Page
import lynks.util.createDummyComment
import lynks.util.createDummyEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class CommentEndpointTest: ServerTest() {

    @BeforeEach
    fun createEntries() {
        createDummyEntry("e1", "title1", "content1", EntryType.LINK)
        Thread.sleep(10)// prevent having same creation timestamp
        createDummyEntry("e2", "title2", "content2", EntryType.NOTE)
        createDummyComment("c1", "e1", "comment content")
        Thread.sleep(10)
        createDummyComment("c2", "e2", "comment content2")
        Thread.sleep(10)
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
        assertThat(created.entryId).isEqualTo(EntryId("e1"))
        assertThat(created.plainContent).isEqualTo(newComment.plainContent)
        assertThat(created.renderedContent).isEqualTo("<p>some markdown text</p>\n")
        assertThat(created.entryId).isEqualTo(EntryId("e1"))
        assertThat(created.dateCreated).isEqualTo(created.dateUpdated)
        val retrieved = get("/entry/{entryId}/comments/{id}", created.entryId.value, created.id.value)
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
        assertThat(comment.id).isEqualTo(CommentId("c2"))
        assertThat(comment.entryId).isEqualTo(EntryId("e2"))
        assertThat(comment.plainContent).isEqualTo("comment content2")
        assertThat(comment.dateCreated).isAfter(Instant.EPOCH)
        assertThat(comment.dateUpdated).isAfter(Instant.EPOCH)
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
        val updatedComment = NewComment(CommentId("c2"), "modified")
        val updated = given()
                .contentType(ContentType.JSON)
                .body(updatedComment)
                .When()
                .put("/entry/{entryId}/comments", "e2")
                .then()
                .statusCode(200)
                .extract().to<Comment>()
        assertThat(updated.plainContent).isEqualTo("modified")
        assertThat(updated.renderedContent).isEqualTo("<p>modified</p>\n")
        assertThat(updated.dateUpdated).isNotEqualTo(updated.dateCreated)
        val retrieved = get("/entry/{entryId}/comments/{id}", "e2", "c2")
                .then().extract().to<Comment>()
        assertThat(retrieved).isEqualTo(updated)
    }

    @Test
    fun testUpdateCommentReturnsNotFound() {
        val updatedComment = NewComment(CommentId("c2"), "modified")
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
                .extract().to<Page<Comment>>()
        assertThat(comments.page).isEqualTo(1)
        assertThat(comments.total).isEqualTo(2)
        assertThat(comments.content).hasSize(2).extracting<CommentId> { it.id }
            .containsExactlyInAnyOrder(CommentId("c1"), CommentId("c3"))
    }

    @Test
    fun testCommentPaging() {
        val comments = given()
                .queryParam("page", 2)
                .queryParam("size", 1)
                .When()
                .get("/entry/{entryId}/comments", "e1")
                .then()
                .statusCode(200)
                .extract().to<Page<Comment>>()
        // newest comment first
        assertThat(comments.page).isEqualTo(2)
        assertThat(comments.size).isEqualTo(1)
        assertThat(comments.total).isEqualTo(2)
        assertThat(comments.content).hasSize(1).extracting<CommentId> { it.id }
            .containsExactly(CommentId("c3"))
    }

}
