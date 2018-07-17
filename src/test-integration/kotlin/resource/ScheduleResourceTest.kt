package resource

import common.EntryType
import common.ServerTest
import io.restassured.RestAssured.*
import io.restassured.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import schedule.*
import util.createDummyEntry
import util.createDummyReminder
import java.time.ZoneId

class ScheduleResourceTest: ServerTest() {

    @BeforeEach
    fun createEntries() {
        createDummyEntry("e1", "title1", "content1", EntryType.LINK)
        createDummyReminder("r1", "e1", ScheduleType.REMINDER, (System.currentTimeMillis() + 1.2e+6).toLong().toString())
    }

    @Test
    fun testGetAllReminders() {
        val reminders = get("/reminder")
                .then()
                .statusCode(200)
                .extract().to<List<Schedule>>()
        assertThat(reminders).hasSize(1)
        assertThat(reminders).extracting("scheduleId").containsOnly("r1")
        assertThat(reminders).extracting("entryId").containsOnly("e1")
        assertThat(reminders).extracting("type").containsOnly(ScheduleType.REMINDER.toString())
    }

    @Test
    fun testGetReminderById() {
        val reminder = get("/reminder/{id}", "r1")
                .then()
                .statusCode(200)
                .extract().to<Reminder>()
        assertThat(reminder.scheduleId).isEqualTo("r1")
        assertThat(reminder.entryId).isEqualTo("e1")
        assertThat(reminder.type).isEqualTo(ScheduleType.REMINDER)
    }

    @Test
    fun testGetInvalidReminder() {
        get("/reminder/{id}", "invalid")
                .then()
                .statusCode(404)
    }

    @Test
    fun testCreateReminder() {
        val reminder = NewReminder(null, "e1", ScheduleType.RECURRING, "every 30 minutes", ZoneId.systemDefault().id)
        val created = given()
                .contentType(ContentType.JSON)
                .body(reminder)
                .When()
                .post("/reminder")
                .then()
                .statusCode(201)
                .extract().to<RecurringReminder>()

        assertThat(created.scheduleId).isNotNull()
        assertThat(created.entryId).isEqualTo(reminder.entryId)
        assertThat(created.type).isEqualTo(reminder.type)
        assertThat(created.spec).isEqualTo(reminder.spec)
        assertThat(created.tz).isEqualTo(reminder.tz)

        val retrieved = get("/reminder/{id}", created.scheduleId)
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
        val reminder = NewReminder("r1", "e1", ScheduleType.RECURRING, "every 30 minutes", "Asia/Singapore")
        val updated = given()
                .contentType(ContentType.JSON)
                .body(reminder)
                .When()
                .put("/reminder")
                .then()
                .statusCode(200)
                .extract().to<RecurringReminder>()
        assertThat(updated.scheduleId).isEqualTo(reminder.scheduleId)
        assertThat(updated.entryId).isEqualTo(reminder.entryId)
        assertThat(updated.type).isEqualTo(reminder.type)
        assertThat(updated.spec).isEqualTo(reminder.spec)
        assertThat(updated.tz).isEqualTo(reminder.tz)

        val retrieved = get("/reminder/{id}", reminder.scheduleId)
                .then()
                .statusCode(200)
                .extract().to<RecurringReminder>()
        assertThat(updated).isEqualTo(retrieved)
    }

    @Test
    fun testUpdateReminderReturnsNotFound() {
        val reminder = NewReminder("invalid", "e1", ScheduleType.RECURRING, "every 30 minutes", ZoneId.systemDefault().id)
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

}