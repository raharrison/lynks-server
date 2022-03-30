package lynks.resource

import io.restassured.RestAssured.get
import io.restassured.RestAssured.given
import lynks.common.EntryType
import lynks.common.ServerTest
import lynks.common.page.Page
import lynks.notify.Notification
import lynks.notify.NotificationType
import lynks.util.createDummyEntry
import lynks.util.createDummyNotification
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NotifyResourceTest : ServerTest() {

    @BeforeEach
    fun createEntries() {
        createDummyEntry("e1", "title1", "content1", EntryType.LINK)
        createDummyNotification("n1", NotificationType.PROCESSED, "processed", "e1")
        createDummyNotification("n2", NotificationType.ERROR, "error", "e1")
    }

    @Test
    fun testGetNotificationPage() {
        val notifications = given()
            .queryParam("page", 2)
            .queryParam("size", 1)
            .When()
            .get("/notifications")
            .then()
            .statusCode(200)
            .extract().to<Page<Notification>>()
        // newest notification first
        assertThat(notifications.page).isEqualTo(2)
        assertThat(notifications.size).isEqualTo(1)
        assertThat(notifications.total).isEqualTo(2)
        assertThat(notifications.content).hasSize(1).extracting("id").containsExactly("n1")
    }

    @Test
    fun testGetNotification() {
        val notification = get("/notifications/{id}", "n1")
            .then()
            .statusCode(200)
            .extract().to<Notification>()
        assertThat(notification.id).isEqualTo("n1")
        assertThat(notification.type).isEqualTo(NotificationType.PROCESSED)
        assertThat(notification.message).isEqualTo("processed")
        assertThat(notification.read).isFalse()
        assertThat(notification.entryId).isEqualTo("e1")
        assertThat(notification.entryTitle).isEqualTo("title1")
        assertThat(notification.entryType).isEqualTo(EntryType.LINK)
    }

    @Test
    fun testGetNotificationNotFound() {
        get("/notifications/{id}", "notfound")
            .then()
            .statusCode(404)
    }

    @Test
    fun testGetUnreadCount() {
        get("/notifications/unread")
            .then()
            .statusCode(200)
            .body("unread", equalTo(2))
    }

    @Test
    fun testReadNotification() {
        given()
            .When()
            .post("/notifications/{id}/read", "n1")
            .then()
            .statusCode(200)
        val notification = get("/notifications/{id}", "n1")
            .then()
            .statusCode(200)
            .extract().to<Notification>()
        assertThat(notification.id).isEqualTo("n1")
        assertThat(notification.read).isTrue()
    }

    @Test
    fun testReadNotificationNotFound() {
        given()
            .When()
            .post("/notifications/{id}/read", "notfound")
            .then()
            .statusCode(404)
    }

    @Test
    fun testUnreadNotification() {
        given()
            .When()
            .post("/notifications/{id}/read", "n2")
            .then()
            .statusCode(200)
        given()
            .When()
            .post("/notifications/{id}/unread", "n2")
            .then()
            .statusCode(200)
        val notification = get("/notifications/{id}", "n2")
            .then()
            .statusCode(200)
            .extract().to<Notification>()
        assertThat(notification.id).isEqualTo("n2")
        assertThat(notification.read).isFalse()
    }

    @Test
    fun testUnreadNotificationNotFound() {
        given()
            .When()
            .post("/notifications/{id}/unread", "notfound")
            .then()
            .statusCode(404)
    }

    @Test
    fun testMarkAllRead() {
        given()
            .When()
            .post("/notifications/markAllRead")
            .then()
            .statusCode(200)
            .body("read", equalTo(2))
    }

}
