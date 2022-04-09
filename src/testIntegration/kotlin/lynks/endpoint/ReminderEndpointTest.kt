package lynks.endpoint

import io.restassured.RestAssured.*
import io.restassured.http.ContentType
import lynks.common.EntryType
import lynks.common.ServerTest
import lynks.notify.NotificationMethod
import lynks.reminder.*
import lynks.util.createDummyEntry
import lynks.util.createDummyReminder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.ZoneId

class ReminderEndpointTest : ServerTest() {

    @BeforeEach
    fun createEntries() {
        createDummyEntry("e1", "title1", "content1", EntryType.LINK)
        createDummyReminder("r1", "e1", ReminderType.ADHOC, listOf(NotificationMethod.WEB, NotificationMethod.EMAIL),
            "message", (System.currentTimeMillis() + 1.2e+6).toLong().toString(), status = ReminderStatus.ACTIVE)
    }

    @Test
    fun testGetAllReminders() {
        val reminders = get("/reminder")
                .then()
                .statusCode(200)
                .extract().to<List<Reminder>>()
        assertThat(reminders).hasSize(1)
        assertThat(reminders).extracting("reminderId").containsOnly("r1")
        assertThat(reminders).extracting("entryId").containsOnly("e1")
        assertThat(reminders).extracting("type").containsOnly(ReminderType.ADHOC.name.lowercase())
        assertThat(reminders).extracting("notifyMethods")
            .containsOnly(listOf(NotificationMethod.WEB.name.lowercase(), NotificationMethod.EMAIL.name.lowercase()))
        assertThat(reminders).extracting("message").containsOnly("message")
        assertThat(reminders).extracting("status").containsOnly(ReminderStatus.ACTIVE.name.lowercase())
        assertThat(reminders).extracting("dateCreated").doesNotContainNull()
        assertThat(reminders).extracting("dateUpdated").doesNotContainNull()
    }

    @Test
    fun testGetReminderById() {
        val reminder = get("/reminder/{id}", "r1")
                .then()
                .statusCode(200)
                .extract().to<AdhocReminder>()
        assertThat(reminder.reminderId).isEqualTo("r1")
        assertThat(reminder.entryId).isEqualTo("e1")
        assertThat(reminder.type).isEqualTo(ReminderType.ADHOC)
        assertThat(reminder.notifyMethods).containsExactly(NotificationMethod.WEB, NotificationMethod.EMAIL)
        assertThat(reminder.message).isEqualTo("message")
        assertThat(reminder.status).isEqualTo(ReminderStatus.ACTIVE)
        assertThat(reminder.dateCreated).isPositive().isEqualTo(reminder.dateUpdated)
    }

    @Test
    fun testGetInvalidReminder() {
        get("/reminder/{id}", "invalid")
                .then()
                .statusCode(404)
    }

    @Test
    fun testCreateReminder() {
        val reminder = NewReminder(null, "e1", ReminderType.RECURRING, listOf(NotificationMethod.WEB, NotificationMethod.EMAIL),
                "message", "every 30 minutes", ZoneId.systemDefault().id, status = ReminderStatus.DISABLED)
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
        assertThat(created.notifyMethods).containsExactly(NotificationMethod.WEB, NotificationMethod.EMAIL)
        assertThat(created.message).isEqualTo("message")
        assertThat(created.spec).isEqualTo(reminder.spec)
        assertThat(created.tz).isEqualTo(reminder.tz)
        assertThat(created.status).isEqualTo(ReminderStatus.DISABLED)
        assertThat(created.dateCreated).isEqualTo(created.dateUpdated)

        val retrieved = get("/reminder/{id}", created.reminderId)
                .then()
                .statusCode(200)
                .extract().to<RecurringReminder>()
        assertThat(created).isEqualTo(retrieved)

        val all = get("/reminder")
                .then()
                .statusCode(200)
                .extract().to<List<Any>>()
        assertThat(all).hasSize(2)
    }

    @Test
    fun testUpdateReminder() {
        val reminder = NewReminder(
            "r1", "e1", ReminderType.RECURRING,
            listOf(NotificationMethod.EMAIL), "updated", "every 30 minutes", "Asia/Singapore",
            ReminderStatus.DISABLED
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
        assertThat(updated.notifyMethods).containsExactly(NotificationMethod.EMAIL)
        assertThat(updated.message).isEqualTo("updated")
        assertThat(updated.spec).isEqualTo(reminder.spec)
        assertThat(updated.tz).isEqualTo(reminder.tz)
        assertThat(updated.status).isEqualTo(ReminderStatus.DISABLED)
        assertThat(updated.dateUpdated).isNotEqualTo(updated.dateCreated)

        val retrieved = get("/reminder/{id}", reminder.reminderId)
                .then()
                .statusCode(200)
                .extract().to<RecurringReminder>()
        assertThat(updated).isEqualTo(retrieved)
    }

    @Test
    fun testUpdateReminderReturnsNotFound() {
        val reminder = NewReminder(
            "invalid", "e1", ReminderType.RECURRING,
            listOf(NotificationMethod.WEB), "", "every 30 minutes", ZoneId.systemDefault().id,
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
    fun testValidateSchedule() {
        val nextFireTimes = given()
            .contentType(ContentType.TEXT)
            .body("every day 17:00")
            .When()
            .post("/reminder/validate")
            .then()
            .statusCode(200)
            .extract().to<List<String>>()
        assertThat(nextFireTimes).hasSize(5)
    }

    @Test
    fun testValidScheduleInvalidDefinition() {
        given()
            .contentType(ContentType.TEXT)
            .body("every invalid of may 17:00")
            .When()
            .post("/reminder/validate")
            .then()
            .statusCode(400)
    }

}
