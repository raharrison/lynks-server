package lynks.endpoint

import io.restassured.RestAssured.*
import io.restassured.http.ContentType
import lynks.common.EntryType
import lynks.common.ServerTest
import lynks.common.page.Page
import lynks.user.*
import lynks.util.createDummyEntry
import lynks.util.createDummyUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UserEndpointTest : ServerTest() {

    @BeforeEach
    fun setup() {
        createDummyUser("user1", "user1@mail.com", "Bob Smith")
    }

    @Test
    fun testGetDefaultUser() {
        get("/user")
            .then()
            .statusCode(401)
    }

    @Test
    fun testGetUser() {
        // created user
        createDummyUser("user2", "user2@mail.com", "Bert Smith")
        val user = get("/user/{id}", "user2")
            .then()
            .statusCode(200)
            .extract().to<User>()
        assertThat(user.username).isEqualTo("user2")
        assertThat(user.email).isEqualTo("user2@mail.com")
        assertThat(user.displayName).isEqualTo("Bert Smith")
        assertThat(user.dateCreated).isEqualTo(user.dateUpdated)
    }

    @Test
    fun testGetUserNotFound() {
        get("/user/invalid")
            .then()
            .statusCode(401)
    }

    @Test
    fun testRegisterUser() {
        val registered = given()
            .contentType(ContentType.JSON)
            .body(AuthRequest("user2", "pass"))
            .When()
            .post("/user/register")
            .then()
            .statusCode(201)
            .extract().to<User>()
        assertThat(registered.username).isEqualTo("user2")
        assertThat(registered.email).isNull()
        assertThat(registered.displayName).isNull()
        assertThat(registered.dateCreated).isEqualTo(registered.dateUpdated)
        val user = get("/user/{id}", registered.username)
            .then()
            .statusCode(200)
            .extract().to<User>()
        assertThat(user).isEqualTo(registered)
    }

    @Test
    fun testRegisterUserAlreadyExists() {
        given()
            .contentType(ContentType.JSON)
            .body(AuthRequest("user1", "pass"))
            .When()
            .post("/user/register")
            .then()
            .statusCode(400)
    }

    @Test
    fun testChangePassword() {
        given()
            .contentType(ContentType.JSON)
            .body(AuthRequest("user2", "pass123"))
            .When()
            .post("/user/register")
            .then()
            .statusCode(201)
        given()
            .contentType(ContentType.JSON)
            .body(ChangePasswordRequest("user2", "pass123", "pass456"))
            .When()
            .post("/user/changePassword")
            .then()
            .statusCode(200)
    }

    @Test
    fun testChangePasswordInvalidOldPassword() {
        given()
            .contentType(ContentType.JSON)
            .body(AuthRequest("user2", "pass123"))
            .When()
            .post("/user/register")
            .then()
            .statusCode(201)
        given()
            .contentType(ContentType.JSON)
            .body(ChangePasswordRequest("user2", "invalid", "pass456"))
            .When()
            .post("/user/changePassword")
            .then()
            .statusCode(400)
    }

    @Test
    fun testUpdateUser() {
        createDummyUser("user2", "user2@mail.com", "Bert Smith")
        val original = get("/user/{id}", "user2")
            .then()
            .statusCode(200)
            .extract().to<User>()
        assertThat(original.username).isEqualTo("user2")
        assertThat(original.email).isEqualTo("user2@mail.com")
        assertThat(original.displayName).isEqualTo("Bert Smith")
        assertThat(original.digest).isFalse()
        assertThat(original.dateCreated).isEqualTo(original.dateUpdated)
        val updated = given()
            .contentType(ContentType.JSON)
            .body(UserUpdateRequest(original.username, "updated@mail.com", "Bart Smith", true))
            .When()
            .put("/user")
            .then()
            .statusCode(200)
            .extract().to<User>()
        assertThat(updated).isNotNull()
        assertThat(updated.username).isEqualTo(original.username)
        assertThat(updated.email).isEqualTo("updated@mail.com")
        assertThat(updated.displayName).isEqualTo("Bart Smith")
        assertThat(updated.digest).isTrue()
        assertThat(updated.dateCreated).isNotEqualTo(updated.dateUpdated)
        val user = get("/user/{id}", updated.username)
            .then()
            .statusCode(200)
            .extract().to<User>()
        assertThat(user).isEqualTo(updated)
    }

    @Test
    fun testUpdateUserNotFound() {
        given()
            .contentType(ContentType.JSON)
            .body(UserUpdateRequest("invalid", "updated@mail.com", "Bill Smith"))
            .When()
            .put("/user")
            .then()
            .statusCode(404)
    }

    @Test
    fun testUpdateUserInvalidEmail() {
        given()
            .contentType(ContentType.JSON)
            .body(UserUpdateRequest("user1", "invalid"))
            .When()
            .put("/user")
            .then()
            .statusCode(400)
    }

    @Test
    fun testGetUserActivityLog() {
        createDummyEntry("e1", "note1", "note content", EntryType.NOTE)
        post("/entry/{id}/star", "e1")
        post("/entry/{id}/unstar", "e1")

        val activityLog = get("/user/activity")
            .then()
            .statusCode(200)
            .extract().to<Page<ActivityLogItem>>()

        assertThat(activityLog.total).isEqualTo(2)
        assertThat(activityLog.page).isEqualTo(1)
        assertThat(activityLog.content).hasSize(2)
        assertThat(activityLog.content).extracting("id").doesNotHaveDuplicates()
        assertThat(activityLog.content).extracting("entryId").containsOnly("e1")
        assertThat(activityLog.content).extracting("details").doesNotHaveDuplicates()
        assertThat(activityLog.content).extracting("entryType").containsOnly(EntryType.NOTE.name.lowercase())
        assertThat(activityLog.content).extracting("entryTitle").containsOnly("note1")
        assertThat(activityLog.content).extracting("details").doesNotHaveDuplicates()
        assertThat(activityLog.content).extracting("timestamp").doesNotContainNull()
    }


}
