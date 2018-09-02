package resource

import common.*
import io.restassured.RestAssured.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import schedule.Schedule
import schedule.ScheduleType
import util.createDummyEntry
import util.createDummyReminder
import java.time.ZoneId

class EntryResourceTest: ServerTest() {

    @BeforeEach
    fun createEntries() {
        createDummyEntry("e1", "expedition", "some content here", EntryType.LINK)
        createDummyReminder("r1", "e1", ScheduleType.REMINDER, (System.currentTimeMillis() + 1.2e+6).toLong().toString())
        Thread.sleep(10)// prevent having same creation timestamp
        createDummyEntry("e2", "changeover", "other content there", EntryType.NOTE)
        Thread.sleep(10)
        createDummyEntry("e3", "megabyte expedition", "nothing important", EntryType.NOTE)
        Thread.sleep(10)
        createDummyEntry("e4", "refusal", "http://google.co.uk/content", EntryType.LINK)
    }

    @Test
    fun testGetSingleReturnsNotFound() {
        get("/entry/{id}", "invalid")
                .then()
                .statusCode(404)
    }

    @Test
    fun testGetSingleNote() {
        val note = get("/entry/{id}", "e2")
                .then()
                .statusCode(200)
                .extract().to<Note>()
        assertThat(note.id).isEqualTo("e2")
        assertThat(note.title).isEqualTo("changeover")
        assertThat(note.plainText).isEqualTo("other content there")
        assertThat(note.tags).isEmpty()
        assertThat(note.collections).isEmpty()
        assertThat(note.type).isEqualTo(EntryType.NOTE)
    }

    @Test
    fun testGetSingleLink() {
        val link = get("/entry/{id}", "e4")
                .then()
                .statusCode(200)
                .extract().to<Link>()
        assertThat(link.id).isEqualTo("e4")
        assertThat(link.title).isEqualTo("refusal")
        assertThat(link.url).isEqualTo("http://google.co.uk/content")
        assertThat(link.tags).isEmpty()
        assertThat(link.collections).isEmpty()
        assertThat(link.type).isEqualTo(EntryType.LINK)
    }

    @Test
    fun testGetAll() {
        val entries = get("/entry")
                .then()
                .statusCode(200)
                .extract().to<List<Entry>>()
        assertThat(entries).hasSize(4).extracting("id").containsExactlyInAnyOrder("e1", "e2", "e3", "e4")
    }

    @Test
    fun testGetPaged() {
        val entries = given()
                .queryParam("offset", 1)
                .queryParam("limit", 1)
                .When()
                .get("/entry")
                .then()
                .statusCode(200)
                .extract().to<List<Entry>>()
        assertThat(entries).hasSize(1).extracting("id").containsExactly("e3")

        val entries2 = given()
                .queryParam("offset", 1)
                .queryParam("limit", 5)
                .When()
                .get("/entry")
                .then()
                .statusCode(200)
                .extract().to<List<Entry>>()
        assertThat(entries2).hasSize(3).extracting("id").containsExactlyInAnyOrder("e1", "e2", "e3")
    }

    @Test
    fun testSearchNonMatch() {
        val entries = given()
                .queryParam("q", "aggdegerg")
                .When()
                .get("/entry/search")
                .then()
                .statusCode(200)
                .extract().to<List<Entry>>()
        assertThat(entries).isEmpty()
    }

    @Test
    fun testSearchByTitle() {
        val entries = given()
                .queryParam("q", "expedition")
                .When()
                .get("/entry/search")
                .then()
                .statusCode(200)
                .extract().to<List<Entry>>()
        assertThat(entries).hasSize(2).extracting("id").containsExactlyInAnyOrder("e1", "e3")
    }

    @Test
    fun testSearchByContent() {
        val entries = given()
                .queryParam("q", "content")
                .When()
                .get("/entry/search")
                .then()
                .statusCode(200)
                .extract().to<List<Entry>>()
        assertThat(entries).hasSize(3).extracting("id").containsExactlyInAnyOrder("e1", "e2", "e4")
    }

    @Test
    fun testSearchPaging() {
        val entries = given()
                .queryParam("offset", 1)
                .queryParam("limit", 1)
                .queryParam("q", "content")
                .When()
                .get("/entry/search")
                .then()
                .statusCode(200)
                .extract().to<List<Entry>>()
        assertThat(entries).hasSize(1).extracting("id").containsExactly("e2")
    }

    @Test
    fun testGetInvalidVersion() {
        get("/entry/{id}/{version}", "e2", 2)
            .then()
            .statusCode(404)
    }

    @Test
    fun testGetRemindersForEntry() {
        val reminders = get("/entry/{id}/reminder", "e1")
                .then()
                .statusCode(200)
                .extract()
                .to<List<Schedule>>()
        assertThat(reminders).hasSize(1)
        assertThat(reminders).extracting("scheduleId").containsOnly("r1")
        assertThat(reminders).extracting("entryId").containsOnly("e1")
        assertThat(reminders).extracting("type").containsOnly(ScheduleType.REMINDER.toString())
        assertThat(reminders).extracting("spec").isNotEmpty()
        assertThat(reminders).extracting("tz").containsOnly(ZoneId.systemDefault().id)

        val none = get("/entry/{id}/reminder", "e2")
                .then()
                .statusCode(200)
                .extract()
                .to<List<Any>>()
        assertThat(none).isEmpty()
    }

    @Test
    fun testSetStarInvalidEntry() {
        post("/entry/{id}/star", "invalid")
                .then()
                .statusCode(404)
        post("/entry/{id}/unstar", "invalid")
                .then()
                .statusCode(404)
    }

    @Test
    fun testSetEntryStar() {
        val read = post("/entry/{id}/star", "e1")
                .then()
                .statusCode(200)
                .extract().to<Link>()
        assertThat(read.starred).isTrue()
        val retrieved = get("/entry/{id}", "e1")
                .then()
                .statusCode(200)
                .extract().to<Link>()
        assertThat(retrieved.starred).isTrue()
    }

    @Test
    fun testSetEntryUnstar() {
        post("/entry/{id}/star", "e1")
        val read = post("/entry/{id}/unstar", "e1")
                .then()
                .statusCode(200)
                .extract().to<Link>()
        assertThat(read.starred).isFalse()
        val retrieved = get("/entry/{id}", "e1")
                .then()
                .statusCode(200)
                .extract().to<Link>()
        assertThat(retrieved.starred).isFalse()
    }
}