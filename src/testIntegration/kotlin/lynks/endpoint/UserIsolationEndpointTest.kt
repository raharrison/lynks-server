package lynks.endpoint

import io.restassured.RestAssured.*
import io.restassured.http.ContentType
import lynks.common.*
import lynks.common.page.Page
import lynks.digest.Digests
import lynks.group.GroupIdSet
import lynks.notify.NotificationMethod
import lynks.notify.NotificationType
import lynks.reminder.NewReminder
import lynks.reminder.ReminderStatus
import lynks.reminder.ReminderType
import lynks.resource.FileStore
import lynks.resource.ResourceManager
import lynks.resource.ResourceRepository
import lynks.resource.ResourceType
import lynks.util.*
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.equalTo
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

// Every request here acts as the default user, against data that belongs to another user
class UserIsolationEndpointTest : ServerTest() {

    private val theirNote = "theirs"

    @BeforeEach
    fun createOtherUsersData() {
        createDummyUser("other-user", id = OTHER_USER)
        createDummyEntry(theirNote, "their secret note", "secret content", EntryType.NOTE, userId = OTHER_USER)
        createDummyEntry("theirLink", "their link", "https://example.com/theirs", EntryType.LINK, userId = OTHER_USER)
        createDummyTag("theirTag", "their tag", OTHER_USER)
        createDummyCollection("theirCol", "their collection", userId = OTHER_USER)
        createDummyComment("theirComment", theirNote, "their comment")
        createDummyReminder("theirReminder", theirNote, ReminderType.ADHOC, listOf(NotificationMethod.PUSH), spec = "100")
        createDummyNotification("theirNotif", NotificationType.PROCESSED, "their message", theirNote, OTHER_USER)
        ResourceManager(FileStore(), ResourceRepository())
            .saveGeneratedResource(ResourceId("theirResource"), EntryId(theirNote), "file.txt", "txt", ResourceType.UPLOAD, 1)
        refreshGroups()
    }

    @Test
    fun testEntriesAreNotListedOrFound() {
        assertThat(get("/entry").then().statusCode(200).extract().to<Page<Map<String, Any>>>().total).isZero()
        assertThat(get("/note").then().statusCode(200).extract().to<Page<Map<String, Any>>>().total).isZero()
        assertThat(get("/entry/search?q=secret").then().statusCode(200).extract().to<Page<Map<String, Any>>>().total).isZero()
        assertThat(get("/entry/suggest?q=their").then().statusCode(200).extract().to<Page<Map<String, Any>>>().total).isZero()
        assertThat(get("/entry/resolve?ids=$theirNote").then().statusCode(200).extract().to<List<Any>>()).isEmpty()
        get("/entry/{id}", theirNote).then().statusCode(404)
        get("/note/{id}", theirNote).then().statusCode(404)
        get("/note/{id}/{version}", theirNote, 1).then().statusCode(404)
        assertThat(get("/entry/{id}/history", theirNote).then().statusCode(200).extract().to<List<Any>>()).isEmpty()
        assertThat(get("/entry/{id}/audit", theirNote).then().statusCode(200).extract().to<List<Any>>()).isEmpty()
        assertThat(
            given().body("https://example.com/theirs").post("/link/checkExisting")
                .then().statusCode(200).extract().to<List<Any>>()
        ).isEmpty()
    }

    @Test
    fun testEntriesCannotBeChanged() {
        given().contentType(ContentType.JSON).body(NewNote(EntryId(theirNote), "taken", "overwritten"))
            .When().put("/note").then().statusCode(404)
        post("/entry/{id}/star", theirNote).then().statusCode(404)
        post("/note/{id}/revert/{version}", theirNote, 1).then().statusCode(404)
        post("/link/{id}/read", "theirLink").then().statusCode(404)
        given().contentType(ContentType.JSON).body(GroupIdSet())
            .When().put("/entry/{id}/groups", theirNote).then().statusCode(404)
        given().contentType(ContentType.JSON).body(emptyMap<String, String>())
            .When().post("/entry/{id}/task/{taskId}", theirNote, "task").then().statusCode(404)
        delete("/note/{id}", theirNote).then().statusCode(404)
        delete("/link/{id}", "theirLink").then().statusCode(404)
    }

    @Test
    fun testCannotUseTheirGroups() {
        assertThat(get("/tag").then().statusCode(200).extract().to<List<Any>>()).isEmpty()
        assertThat(get("/collection").then().statusCode(200).extract().to<List<Any>>()).isEmpty()
        get("/tag/{id}", "theirTag").then().statusCode(404)
        delete("/collection/{id}", "theirCol").then().statusCode(404)
        given().contentType(ContentType.JSON).body(NewNote(null, "mine", "content", listOf("theirTag")))
            .When().post("/note").then().statusCode(400)
    }

    @Test
    fun testCommentsAreScoped() {
        assertThat(
            get("/entry/{id}/comments", theirNote).then().statusCode(200)
                .extract().to<Page<Map<String, Any>>>().total
        ).isZero()
        get("/entry/{id}/comments/{commentId}", theirNote, "theirComment").then().statusCode(404)
        given().contentType(ContentType.JSON).body(mapOf("plainContent" to "intruder"))
            .When().post("/entry/{id}/comments", theirNote).then().statusCode(404)
        delete("/entry/{id}/comments/{commentId}", theirNote, "theirComment").then().statusCode(404)
    }

    @Test
    fun testResourcesAreScoped() {
        assertThat(get("/entry/{id}/resource", theirNote).then().statusCode(200).extract().to<List<Any>>()).isEmpty()
        get("/entry/{id}/resource/{resourceId}", theirNote, "theirResource").then().statusCode(404)
        get("/entry/{id}/resource/{resourceId}/info", theirNote, "theirResource").then().statusCode(404)
        given().multiPart("file", "upload.txt", byteArrayOf(1, 2, 3))
            .When().post("/entry/{id}/resource", theirNote).then().statusCode(404)
        delete("/entry/{id}/resource/{resourceId}", theirNote, "theirResource").then().statusCode(404)
    }

    @Test
    fun testRemindersAreScoped() {
        assertThat(get("/reminder").then().statusCode(200).extract().to<Page<Map<String, Any>>>().total).isZero()
        assertThat(get("/entry/{id}/reminder", theirNote).then().statusCode(200).extract().to<List<Any>>()).isEmpty()
        get("/reminder/{id}", "theirReminder").then().statusCode(404)
        delete("/reminder/{id}", "theirReminder").then().statusCode(404)
        val reminder = NewReminder(
            null, EntryId(theirNote), ReminderType.ADHOC, listOf(NotificationMethod.PUSH),
            "mine", 100, null, ZoneId.systemDefault().id, ReminderStatus.ACTIVE
        )
        given().contentType(ContentType.JSON).body(reminder).When().post("/reminder").then().statusCode(400)
    }

    @Test
    fun testNotificationsAreScoped() {
        assertThat(get("/notifications").then().statusCode(200).extract().to<Page<Map<String, Any>>>().total).isZero()
        get("/notifications/unread").then().statusCode(200).body("unread", equalTo(0))
        get("/notifications/{id}", "theirNotif").then().statusCode(404)
        post("/notifications/{id}/read", "theirNotif").then().statusCode(404)
        post("/notifications/markAllRead").then().statusCode(200).body("read", equalTo(0))
    }

    @Test
    fun testDigestIsScoped() {
        transaction {
            Digests.insert {
                it[digestId] = "theirDigest"
                it[userId] = OTHER_USER.value
                it[entryIds] = "theirLink"
                it[dateCreated] = OffsetDateTime.now(ZoneOffset.UTC)
            }
        }
        get("/digest").then().statusCode(404)
    }
}
