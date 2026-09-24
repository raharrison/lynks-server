package lynks.endpoint

import io.restassured.RestAssured.*
import io.restassured.http.ContentType
import lynks.common.EntryId
import lynks.common.EntryType
import lynks.common.ReminderId
import lynks.common.ServerTest
import lynks.common.page.Page
import lynks.notify.NotificationMethod
import lynks.reminder.*
import lynks.util.createDummyEntry
import lynks.util.createDummyReminder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.*

class ReminderEndpointTest : ServerTest() {

    private val everyHalfHour = IntervalSchedule(30, IntervalUnit.MINUTES)

    @BeforeEach
    fun createEntries() {
        createDummyEntry("e1", "title1", "content1", EntryType.LINK)
        createDummyReminder(
            "r1", "e1", ReminderType.ADHOC, listOf(NotificationMethod.PUSH, NotificationMethod.JOLT),
            "message", (System.currentTimeMillis() + 1.2e+6).toLong().toString(), status = ReminderStatus.ACTIVE)
    }

    @Test
    fun testGetAllReminders() {
        val page = get("/reminder")
                .then()
                .statusCode(200)
                .extract().to<Page<*>>()
        assertThat(page.total).isEqualTo(1)
        assertThat(page.page).isEqualTo(1)
        val reminders = page.content
        assertThat(reminders).hasSize(1)
        assertThat(reminders).extracting("reminderId").containsOnly("r1")
        assertThat(reminders).extracting("entryId").containsOnly("e1")
        assertThat(reminders).extracting("type").containsOnly(ReminderType.ADHOC.name.lowercase())
        assertThat(reminders).extracting("notifyMethods")
            .containsOnly(listOf(NotificationMethod.PUSH.name.lowercase(), NotificationMethod.JOLT.name.lowercase()))
        assertThat(reminders).extracting("message").containsOnly("message")
        assertThat(reminders).extracting("status").containsOnly(ReminderStatus.ACTIVE.name.lowercase())
        assertThat(reminders).extracting("dateCreated").doesNotContainNull()
        assertThat(reminders).extracting("dateUpdated").doesNotContainNull()
    }

    @Test
    fun testGetAllRemindersPaging() {
        createDummyReminder(
            "r2", "e1", ReminderType.RECURRING, listOf(NotificationMethod.JOLT),
            "msg2", CalendarSchedule(LocalTime.of(9, 0)).toSpec(), status = ReminderStatus.DISABLED
        )
        val page1 = given()
                .queryParam("page", 1)
                .queryParam("size", 1)
                .When()
                .get("/reminder")
                .then()
                .statusCode(200)
                .extract().to<Page<*>>()
        assertThat(page1.total).isEqualTo(2)
        assertThat(page1.page).isEqualTo(1)
        assertThat(page1.size).isEqualTo(1)
        assertThat(page1.content).hasSize(1)
    }

    @Test
    fun testGetReminderById() {
        val reminder = get("/reminder/{id}", "r1")
                .then()
                .statusCode(200)
                .extract().to<AdhocReminder>()
        assertThat(reminder.reminderId).isEqualTo(ReminderId("r1"))
        assertThat(reminder.entryId).isEqualTo(EntryId("e1"))
        assertThat(reminder.type).isEqualTo(ReminderType.ADHOC)
        assertThat(reminder.notifyMethods).containsExactly(NotificationMethod.PUSH, NotificationMethod.JOLT)
        assertThat(reminder.message).isEqualTo("message")
        assertThat(reminder.status).isEqualTo(ReminderStatus.ACTIVE)
        assertThat(reminder.dateCreated).isAfter(Instant.EPOCH).isEqualTo(reminder.dateUpdated)
    }

    @Test
    fun testGetInvalidReminder() {
        get("/reminder/{id}", "invalid")
                .then()
                .statusCode(404)
    }

    @Test
    fun testCreateReminder() {
        val reminder = NewReminder(
            null, EntryId("e1"), ReminderType.RECURRING, listOf(NotificationMethod.PUSH, NotificationMethod.JOLT),
            "message", schedule = everyHalfHour, tz = ZoneId.systemDefault().id, status = ReminderStatus.DISABLED
        )
        val created = given()
                .contentType(ContentType.JSON)
                .body(reminder)
                .When()
                .post("/reminder")
                .then()
                .statusCode(201)
                .extract().to<RecurringReminder>()

        assertThat(created.reminderId).isNotNull()
        assertThat(created.entryId).isEqualTo(reminder.entryId)
        assertThat(created.type).isEqualTo(reminder.type)
        assertThat(created.notifyMethods).containsExactly(NotificationMethod.PUSH, NotificationMethod.JOLT)
        assertThat(created.message).isEqualTo("message")
        assertThat(created.schedule).isEqualTo(reminder.schedule)
        assertThat(created.tz).isEqualTo(reminder.tz)
        assertThat(created.status).isEqualTo(ReminderStatus.DISABLED)
        assertThat(created.dateCreated).isEqualTo(created.dateUpdated)

        val retrieved = get("/reminder/{id}", created.reminderId.value)
                .then()
                .statusCode(200)
                .extract().to<RecurringReminder>()
        assertThat(created).isEqualTo(retrieved)

        val all = get("/reminder")
                .then()
                .statusCode(200)
                .extract().to<Page<*>>()
        assertThat(all.total).isEqualTo(2)
    }

    @Test
    fun testUpdateReminder() {
        val reminder = NewReminder(
            ReminderId("r1"), EntryId("e1"), ReminderType.RECURRING,
            listOf(NotificationMethod.JOLT), "updated", schedule = everyHalfHour, tz = "Asia/Singapore",
            status = ReminderStatus.DISABLED
        )
        val updated = given()
                .contentType(ContentType.JSON)
                .body(reminder)
                .When()
                .put("/reminder")
                .then()
                .statusCode(200)
                .extract().to<RecurringReminder>()
        assertThat(updated.reminderId).isEqualTo(reminder.reminderId)
        assertThat(updated.entryId).isEqualTo(reminder.entryId)
        assertThat(updated.type).isEqualTo(reminder.type)
        assertThat(updated.notifyMethods).containsExactly(NotificationMethod.JOLT)
        assertThat(updated.message).isEqualTo("updated")
        assertThat(updated.schedule).isEqualTo(reminder.schedule)
        assertThat(updated.tz).isEqualTo(reminder.tz)
        assertThat(updated.status).isEqualTo(ReminderStatus.DISABLED)
        assertThat(updated.dateUpdated).isNotEqualTo(updated.dateCreated)

        val retrieved = get("/reminder/{id}", reminder.reminderId?.value)
                .then()
                .statusCode(200)
                .extract().to<RecurringReminder>()
        assertThat(updated).isEqualTo(retrieved)
    }

    @Test
    fun testUpdateReminderReturnsNotFound() {
        val reminder = NewReminder(
            ReminderId("invalid"), EntryId("e1"), ReminderType.RECURRING,
            listOf(NotificationMethod.PUSH), "", schedule = everyHalfHour, tz = ZoneId.systemDefault().id,
            status = ReminderStatus.ACTIVE
        )
        given()
                .contentType(ContentType.JSON)
                .body(reminder)
                .When()
                .put("/reminder")
                .then()
                .statusCode(404)
    }

    @Test
    fun testDeleteReminder() {
        delete("/reminder/{id}", "r1")
                .then()
                .statusCode(200)
        get("/reminder/{id}", "r1")
                .then()
                .statusCode(404)
    }

    @Test
    fun testDeleteInvalidReminder() {
        delete("/reminder/{id}", "invalid")
                .then()
                .statusCode(404)
    }

    @Test
    fun testPreviewSchedule() {
        val fires = given()
            .contentType(ContentType.JSON)
            .body("""{"schedule":{"kind":"calendar","at":"17:00","weekdays":["monday"]},"tz":"Asia/Singapore"}""")
            .When()
            .post("/reminder/preview")
            .then()
            .statusCode(200)
            .extract().to<List<String>>()
        assertThat(fires).hasSize(5)
        assertThat(fires.map { OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.of("Asia/Singapore")) })
            .allSatisfy {
                assertThat(it.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
                assertThat(it.toLocalTime()).isEqualTo(LocalTime.of(17, 0))
            }
    }

    @Test
    fun testPreviewInvalidSchedule() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"schedule":{"kind":"interval","every":0,"unit":"minutes"},"tz":"UTC"}""")
            .When()
            .post("/reminder/preview")
            .then()
            .statusCode(400)
        given()
            .contentType(ContentType.JSON)
            .body("""{"schedule":{"kind":"sometimes"},"tz":"UTC"}""")
            .When()
            .post("/reminder/preview")
            .then()
            .statusCode(400)
    }

    @Test
    fun testCreateRecurringWithoutSchedule() {
        val reminder = NewReminder(
            null, EntryId("e1"), ReminderType.RECURRING, listOf(NotificationMethod.PUSH),
            tz = "UTC", status = ReminderStatus.ACTIVE
        )
        given().contentType(ContentType.JSON).body(reminder).When().post("/reminder").then().statusCode(400)
    }

}
