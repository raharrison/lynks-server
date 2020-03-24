package resource

import common.EntryType
import common.NewNote
import common.Note
import common.ServerTest
import io.restassured.RestAssured.*
import io.restassured.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import util.createDummyCollection
import util.createDummyEntry
import util.createDummyTag

class NoteResourceTest: ServerTest() {

    @BeforeEach
    fun createEntries() {
        createDummyEntry("e1", "title1", "content1", EntryType.LINK)
        Thread.sleep(10)// prevent having same creation timestamp
        createDummyEntry("e2", "title2", "content2", EntryType.NOTE)
        Thread.sleep(10)
        createDummyEntry("e3", "title3", "content3", EntryType.NOTE)
        createDummyTag("t1", "tag1")
        createDummyCollection("c1", "col1")
        post("/tag/refresh")
        post("/collection/refresh")
    }

    @Test
    fun testCreateNote() {
        val newNote = NewNote(null, "title4", "content4", listOf("t1"), listOf("c1"))
        val created = given()
                .contentType(ContentType.JSON)
                .body(newNote)
                .When()
                .post("/note")
                .then()
                .statusCode(201)
                .extract().to<Note>()
        assertThat(created.title).isEqualTo(newNote.title)
        assertThat(created.plainText).isEqualTo(newNote.plainText)
        assertThat(created.markdownText).isEqualTo("<p>content4</p>\n")
        assertThat(created.type).isEqualTo(EntryType.NOTE)
        assertThat(created.tags).hasSize(1).extracting("id").containsExactly("t1")
        assertThat(created.collections).hasSize(1).extracting("id").containsExactly("c1")
        assertThat(created.dateCreated).isEqualTo(created.dateUpdated)
        val retrieved = get("/note/{id}", created.id)
                .then()
                .extract().to<Note>()
        assertThat(created).isEqualToIgnoringGivenFields(retrieved, "props")
    }

    @Test
    fun testCreateNoteWithInvalidGroups() {
        given()
            .contentType(ContentType.JSON)
            .body(NewNote(null, "title4", "content4", listOf("invalid")))
            .When()
            .post("/note")
            .then()
            .statusCode(400)

        given()
            .contentType(ContentType.JSON)
            .body(NewNote(null, "title4", "content4", emptyList(), listOf("invalid")))
            .When()
            .post("/note")
            .then()
            .statusCode(400)
    }

    @Test
    fun testGetNoteReturnsNotFound() {
        get("/note/{id}", "invalid")
                .then()
                .statusCode(404)
    }

    @Test
    fun testGetSingleNote() {
        val note = get("/note/{id}", "e2")
                .then()
                .statusCode(200)
                .extract().to<Note>()
        assertThat(note.id).isEqualTo("e2")
        assertThat(note.title).isEqualTo("title2")
        assertThat(note.plainText).isEqualTo("content2")
        assertThat(note.dateCreated).isEqualTo(note.dateUpdated)
    }

    @Test
    fun testDeleteNote() {
        delete("/note/{id}", "e3")
                .then()
                .statusCode(200)
        get("note/{id}", "e3")
                .then()
                .statusCode(404)
    }

    @Test
    fun testDeleteNoteReturnsNotFound() {
        delete("/note/{id}", "invalid")
                .then()
                .statusCode(404)
    }

    @Test
    fun testCannotDeleteNonNoteEntry() {
        delete("/note/{id}", "e1")
                .then()
                .statusCode(404)
    }

    @Test
    fun testUpdateNote() {
        val updatedNote = NewNote("e2", "title2", "modified", listOf("t1"), listOf("c1"))
        val updated = given()
                .contentType(ContentType.JSON)
                .body(updatedNote)
                .When()
                .put("/note")
                .then()
                .statusCode(200)
                .extract().to<Note>()
        assertThat(updated.plainText).isEqualTo("modified")
        assertThat(updated.markdownText).isEqualTo("<p>modified</p>\n")
        assertThat(updated.tags).hasSize(1).extracting("id").containsExactly("t1")
        assertThat(updated.collections).hasSize(1).extracting("id").containsExactly("c1")
        assertThat(updated.dateCreated).isNotEqualTo(updated.dateUpdated)
        val retrieved = get("/note/{id}", "e2")
                .then().extract().to<Note>()
        assertThat(retrieved).isEqualToIgnoringGivenFields(updated, "dateUpdated", "props")
    }

    @Test
    fun testCannotUpdateNonNote() {
        // e1 = existing link entry
        val updatedLink = NewNote("e1", "title2", "modified", emptyList())
        given()
                .contentType(ContentType.JSON)
                .body(updatedLink)
                .When()
                .put("/note")
                .then()
                .statusCode(404)
    }

    @Test
    fun testUpdateNoteReturnsNotFound() {
        val updatedNote = NewNote("invalid", "title2", "modified", emptyList())
        given()
                .contentType(ContentType.JSON)
                .body(updatedNote)
                .When()
                .put("/note")
                .then()
                .statusCode(404)
    }

    @Test
    fun testGetAllNotes() {
        val notes = given()
                .When()
                .get("/note")
                .then()
                .statusCode(200)
                .extract().to<List<Note>>()
        assertThat(notes).hasSize(2).extracting("id").doesNotHaveDuplicates()
    }

    @Test
    fun testNotePaging() {
        val notes = given()
                .queryParam("offset", 1)
                .queryParam("limit", 1)
                .When()
                .get("/note")
                .then()
                .statusCode(200)
                .extract().to<List<Note>>()
        // newest note first
        assertThat(notes).hasSize(1).extracting("id").containsExactly("e2")
    }

    @Test
    fun testGetInvalidVersion() {
        get("/note/{id}/{version}", "e2", 2)
                .then()
                .statusCode(404)
    }

    @Test
    fun testGetVersion() {
        val newNote = NewNote(null, "title4", "content4", emptyList())
        val created = given()
                .contentType(ContentType.JSON)
                .body(newNote)
                .When()
                .post("/note")
                .then()
                .statusCode(201)
                .extract().to<Note>()

        assertThat(created.version).isOne()
        assertThat(created.title).isEqualTo(newNote.title)
        assertThat(created.plainText).isEqualTo(newNote.plainText)
        assertThat(created.dateCreated).isEqualTo(created.dateUpdated)

        // update
        val updateNote = NewNote(created.id, "edited", "new content", emptyList())
        val updated = given()
                .contentType(ContentType.JSON)
                .body(updateNote)
                .When()
                .put("/note")
                .then()
                .statusCode(200)
                .extract().to<Note>()

        assertThat(updated.title).isEqualTo(updateNote.title)
        assertThat(updated.plainText).isEqualTo(updateNote.plainText)
        assertThat(updated.version).isEqualTo(2)
        assertThat(updated.dateCreated).isNotEqualTo(updated.dateUpdated)

        // retrieve versions
        val original = get("/note/{id}/{version}", created.id, 1)
                .then()
                .statusCode(200)
                .extract().to<Note>()
        assertThat(original.version).isOne()
        assertThat(original.title).isEqualTo(newNote.title)
        assertThat(original.plainText).isEqualTo(newNote.plainText)
        assertThat(original.dateCreated).isEqualTo(original.dateUpdated)

        val current = get("/note/{id}", created.id)
                .then()
                .statusCode(200)
                .extract().to<Note>()
        assertThat(current.version).isEqualTo(2)
        assertThat(current.title).isEqualTo(updateNote.title)
        assertThat(current.plainText).isEqualTo(updateNote.plainText)
        assertThat(current.dateCreated).isNotEqualTo(current.dateUpdated)
    }


}