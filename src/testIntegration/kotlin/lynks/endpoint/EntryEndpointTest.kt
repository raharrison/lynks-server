package lynks.endpoint

import io.restassured.RestAssured.*
import io.restassured.http.ContentType
import lynks.common.*
import lynks.common.page.Page
import lynks.entry.ref.EntryRefSet
import lynks.group.GroupIdSet
import lynks.notify.NotificationMethod
import lynks.reminder.ReminderType
import lynks.util.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.ZoneId

class EntryEndpointTest : ServerTest() {

    @BeforeEach
    fun createEntries() {
        createDummyEntry("e1", "expedition", "some content here", EntryType.LINK)
        createDummyReminder(
            "r1", "e1", ReminderType.ADHOC, listOf(NotificationMethod.JOLT), "message",
            (System.currentTimeMillis() + 1.2e+6).toLong().toString()
        )
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
        assertThat(note.id).isEqualTo(EntryId("e2"))
        assertThat(note.title).isEqualTo("changeover")
        assertThat(note.plainContent).isEqualTo("other content there")
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
        assertThat(link.id).isEqualTo(EntryId("e4"))
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
            .extract().to<Page<*>>()
        assertThat(entries.page).isEqualTo(1)
        assertThat(entries.total).isEqualTo(4)
        assertThat(entries.content).hasSize(4).extracting("id")
            .containsExactlyInAnyOrder("e1", "e2", "e3", "e4")
    }

    @Test
    fun testGetPaged() {
        val entries = given()
            .queryParam("page", 2)
            .queryParam("size", 1)
            .When()
            .get("/entry")
            .then()
            .statusCode(200)
            .extract().to<Page<*>>()
        assertThat(entries.page).isEqualTo(2)
        assertThat(entries.size).isEqualTo(1)
        assertThat(entries.total).isEqualTo(4)
        assertThat(entries.content).hasSize(1).extracting("id").containsExactly("e3")

        val entries2 = given()
            .queryParam("page", 2)
            .queryParam("size", 2)
            .When()
            .get("/entry")
            .then()
            .statusCode(200)
            .extract().to<Page<*>>()
        assertThat(entries2.page).isEqualTo(2)
        assertThat(entries2.size).isEqualTo(2)
        assertThat(entries2.total).isEqualTo(4)
        assertThat(entries2.content).hasSize(2).extracting("id")
            .containsExactlyInAnyOrder("e1", "e2")
    }

    @Test
    fun testInvalidPageParamsReturnBadRequest() {
        listOf("page" to "abc", "page" to "0", "size" to "-1", "direction" to "sideways").forEach { (name, value) ->
            given()
                .queryParam(name, value)
                .get("/entry")
                .then()
                .statusCode(400)
        }
    }

    @Test
    fun testOversizedPageIsCapped() {
        val entries = given()
            .queryParam("size", 100_000)
            .get("/entry")
            .then()
            .statusCode(200)
            .extract().to<Page<*>>()
        assertThat(entries.size).isEqualTo(MAX_PAGE_SIZE)
    }

    @Test
    fun testInvalidVersionReturnsBadRequest() {
        get("/entry/{id}/{version}", "e1", "latest")
            .then()
            .statusCode(400)
    }

    @Test
    fun testRandomOrderSearch() {
        given()
            .queryParam("q", "expedition")
            .queryParam("direction", "rand")
            .get("/entry/search")
            .then()
            .statusCode(200)
    }

    @Test
    fun testMalformedBodyReturnsBadRequest() {
        given()
            .contentType(ContentType.JSON)
            .body("{not json")
            .put("/entry/{id}/groups", "e1")
            .then()
            .statusCode(400)
    }

    @Test
    fun testSuggestByTitle() {
        val entries = given()
            .queryParam("q", "expedition")
            .When()
            .get("/entry/suggest")
            .then()
            .statusCode(200)
            .extract().to<Page<*>>()
        assertThat(entries.page).isEqualTo(1)
        assertThat(entries.content).hasSize(1).extracting("id")
            .containsExactly("e1")
    }

    @Test
    fun testSuggestDoesNotMatchContentOnly() {
        // "content" appears in entry content but in no title
        val entries = given()
            .queryParam("q", "content")
            .When()
            .get("/entry/suggest")
            .then()
            .statusCode(200)
            .extract().to<Page<*>>()
        assertThat(entries.content).isEmpty()
    }

    @Test
    fun testSuggestNoMatch() {
        val entries = given()
            .queryParam("q", "zzznomatch")
            .When()
            .get("/entry/suggest")
            .then()
            .statusCode(200)
            .extract().to<Page<*>>()
        assertThat(entries.content).isEmpty()
    }

    @Test
    fun testSuggestMissingParam() {
        get("/entry/suggest")
            .then()
            .statusCode(400)
    }

    @Test
    fun testSearchNonMatch() {
        val entries = given()
            .queryParam("q", "aggdegerg")
            .When()
            .get("/entry/search")
            .then()
            .statusCode(200)
            .extract().to<Page<*>>()
        assertThat(entries.page).isEqualTo(1)
        assertThat(entries.content).isEmpty()
    }

    @Test
    fun testSearchByTitle() {
        val entries = given()
            .queryParam("q", "expedition")
            .When()
            .get("/entry/search")
            .then()
            .statusCode(200)
            .extract().to<Page<*>>()
        assertThat(entries.page).isEqualTo(1)
        assertThat(entries.content).hasSize(2).extracting("id")
            .containsExactlyInAnyOrder("e1", "e3")
    }

    @Test
    fun testSearchByContent() {
        val entries = given()
            .queryParam("q", "content")
            .When()
            .get("/entry/search")
            .then()
            .statusCode(200)
            .extract().to<Page<*>>()
        assertThat(entries.page).isEqualTo(1)
        assertThat(entries.content).hasSize(3).extracting("id")
            .containsExactlyInAnyOrder("e1", "e2", "e4")
    }

    @Test
    fun testSearchPaging() {
        val entries = given()
            .queryParam("page", 3)
            .queryParam("size", 1)
            .queryParam("q", "content")
            .When()
            .get("/entry/search")
            .then()
            .statusCode(200)
            .extract().to<Page<*>>()
        assertThat(entries.page).isEqualTo(3)
        assertThat(entries.size).isEqualTo(1)
        assertThat(entries.content).hasSize(1).extracting("id")
            .containsExactly("e2")
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
            .to<List<*>>()
        assertThat(reminders).hasSize(1)
        assertThat(reminders).extracting("reminderId").containsOnly("r1")
        assertThat(reminders).extracting("entryId").containsOnly("e1")
        assertThat(reminders).extracting("type").containsOnly(ReminderType.ADHOC.name.lowercase())
        assertThat(reminders).extracting("message").containsOnly("message")
        assertThat(reminders).extracting("fireAt").doesNotContainNull()
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

    @Test
    fun testGetEntryHistory() {
        val entryVersions1 = get("/entry/{id}/history", "e1")
            .then()
            .statusCode(200)
            .extract().to<List<EntryVersion>>()
        assertThat(entryVersions1).hasSize(1)
        assertThat(entryVersions1).extracting<EntryId> { it.id }.containsOnly(EntryId("e1"))
        assertThat(entryVersions1).extracting("version").containsOnly(1)

        updateDummyEntry("e1", "updated", 2)

        val entryVersions2 = get("/entry/{id}/history", "e1")
            .then()
            .statusCode(200)
            .extract().to<List<EntryVersion>>()
        assertThat(entryVersions2).hasSize(2)
        assertThat(entryVersions2).extracting<EntryId> { it.id }.containsOnly(EntryId("e1"))
        assertThat(entryVersions2).extracting("version").containsOnly(1, 2)
        assertThat(entryVersions2).extracting("dateUpdated").doesNotHaveDuplicates()
    }

    @Test
    fun testGetEntryAudit() {
        val entryAudit1 = get("/entry/{id}/audit", "e1")
            .then()
            .statusCode(200)
            .extract().to<List<EntryAuditItem>>()
        assertThat(entryAudit1).isEmpty()

        post("/entry/{id}/star", "e1")
        post("/entry/{id}/star", "e1")

        val entryAudit2 = get("/entry/{id}/audit", "e1")
            .then()
            .statusCode(200)
            .extract().to<List<EntryAuditItem>>()
        assertThat(entryAudit2).hasSize(2)
        assertThat(entryAudit2).extracting<EntryId> { it.entryId }.containsOnly(EntryId("e1"))
        assertThat(entryAudit2).extracting("details").doesNotContainNull()
        assertThat(entryAudit2).extracting("timestamp").doesNotHaveDuplicates()
    }

    @Test
    fun testUpdateEntryGroups() {
        createDummyTag("t1", "tag1")
        createDummyCollection("c1", "col1")
        refreshGroups()
        given()
            .contentType(ContentType.JSON)
            .body(GroupIdSet(listOf("t1"), listOf("c1")))
            .When()
            .put("/entry/{id}/groups", "e2")
            .then()
            .statusCode(200)
        val note = get("/entry/{id}", "e2")
            .then()
            .statusCode(200)
            .extract().to<Note>()
        assertThat(note.tags).extracting("id").containsOnly("t1")
        assertThat(note.collections).extracting("id").containsOnly("c1")
    }

    @Test
    fun testUpdateEntryGroupsNotFound() {
        createDummyTag("t1", "tag1")
        createDummyCollection("c1", "col1")
        refreshGroups()
        given()
            .contentType(ContentType.JSON)
            .body(GroupIdSet(listOf("t1"), listOf("c1")))
            .When()
            .put("/entry/{id}/groups", "invalid")
            .then()
            .statusCode(404)
        given()
            .contentType(ContentType.JSON)
            .body(GroupIdSet(listOf("invalid"), listOf("c1")))
            .When()
            .put("/entry/{id}/groups", "e2")
            .then()
            .statusCode(400)
    }

    @Test
    fun testGetEntryRefs() {
        createDummyEntryRef("e1", "e2", "e1")
        createDummyEntryRef("e1", "e3", "e1")
        createDummyEntryRef("e2", "e1", "e2")
        val refSet = get("/entry/{id}/refs", "e1")
            .then()
            .statusCode(200)
            .extract().to<EntryRefSet>()
        assertThat(refSet.outbound).extracting<EntryId> { it.entryId }
            .containsOnly(EntryId("e2"), EntryId("e3"))
        assertThat(refSet.outbound).extracting("title").containsOnly("changeover", "megabyte expedition")
        assertThat(refSet.outbound).extracting("entryType").containsOnly(EntryType.NOTE)
        assertThat(refSet.inbound).extracting<EntryId> { it.entryId }
            .containsOnly(EntryId("e2"))
        assertThat(refSet.inbound).extracting("title").containsOnly("changeover")
        assertThat(refSet.inbound).extracting("entryType").containsOnly(EntryType.NOTE)
    }

    @Test
    fun testResolveEmptyIds() {
        val result = given()
            .queryParam("ids", "")
            .When()
            .get("/entry/resolve")
            .then()
            .statusCode(200)
            .extract().to<List<*>>()
        assertThat(result).isEmpty()
    }

    @Test
    fun testResolveSingleEntry() {
        val result = given()
            .queryParam("ids", "e2")
            .When()
            .get("/entry/resolve")
            .then()
            .statusCode(200)
            .extract().to<List<*>>()
        assertThat(result).hasSize(1)
        assertThat(result).extracting("id").containsOnly("e2")
        assertThat(result).extracting("title").containsOnly("changeover")
        assertThat(result).extracting("type").containsOnly("note")
    }

    @Test
    fun testResolveMultipleEntries() {
        val result = given()
            .queryParam("ids", "e1,e2,e4")
            .When()
            .get("/entry/resolve")
            .then()
            .statusCode(200)
            .extract().to<List<*>>()
        assertThat(result).hasSize(3)
        assertThat(result).extracting("id").containsExactlyInAnyOrder("e1", "e2", "e4")
        assertThat(result).extracting("title").containsExactlyInAnyOrder("expedition", "changeover", "refusal")
    }

    @Test
    fun testResolveUnknownId() {
        val result = given()
            .queryParam("ids", "unknown")
            .When()
            .get("/entry/resolve")
            .then()
            .statusCode(200)
            .extract().to<List<*>>()
        assertThat(result).isEmpty()
    }

    @Test
    fun testResolveMixedKnownAndUnknown() {
        val result = given()
            .queryParam("ids", "e1,unknown,e3")
            .When()
            .get("/entry/resolve")
            .then()
            .statusCode(200)
            .extract().to<List<*>>()
        assertThat(result).hasSize(2)
        assertThat(result).extracting("id").containsExactlyInAnyOrder("e1", "e3")
    }

}
