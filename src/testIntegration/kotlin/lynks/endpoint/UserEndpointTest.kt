package lynks.endpoint

import io.restassured.RestAssured.*
import io.restassured.http.ContentType
import lynks.common.EntryId
import lynks.common.EntryType
import lynks.common.Environment
import lynks.common.ServerTest
import lynks.common.page.Page
import lynks.entry.EntryAuditService
import lynks.user.*
import lynks.util.*
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UserEndpointTest : ServerTest() {

    @BeforeEach
    fun setup() {
        createDummyUser("user1", "Bob Smith")
    }

    private fun login(username: String, password: String): Int =
        given()
            .contentType(ContentType.JSON)
            .body(AuthRequest(username, password))
            .When()
            .post("/login")
            .then()
            .extract().statusCode()

    @Test
    fun testGetCurrentUser() {
        // with auth disabled every request acts as the default user
        val user = get("/user")
            .then()
            .statusCode(200)
            .extract().to<User>()
        assertThat(user.id).isEqualTo(TEST_USER)
        assertThat(user.username).isEqualTo(Environment.auth.defaultUserName)
    }

    @Test
    fun testDeactivatedDefaultUserIsRejected() {
        activateUser(Environment.auth.defaultUserName, false)
        get("/user").then().statusCode(401)
        get("/entry").then().statusCode(401)
    }

    @Test
    fun testOtherUsersAreNotAddressable() {
        get("/user/{id}", "user1")
            .then()
            .statusCode(404)
    }

    @Test
    fun testLoginUser() {
        given()
            .contentType(ContentType.JSON)
            .body(AuthRequest("user1", DUMMY_USER_PASSWORD))
            .When()
            .post("/login")
            .then()
            .statusCode(200)
            .body("result", Matchers.equalTo(AuthResult.SUCCESS.name.lowercase()))

        given()
            .contentType(ContentType.JSON)
            .body(AuthRequest("user1", "invalid"))
            .When()
            .post("/login")
            .then()
            .statusCode(401)
            .body("result", Matchers.equalTo(AuthResult.INVALID_CREDENTIALS.name.lowercase()))
    }

    @Test
    fun testLoginDeactivatedUser() {
        activateUser("user1", false)
        assertThat(login("user1", DUMMY_USER_PASSWORD)).isEqualTo(401)
    }

    @Test
    fun testLogout() {
        given()
            .contentType(ContentType.JSON)
            .When()
            .post("/logout")
            .then()
            .statusCode(200)
    }

    @Test
    fun testChangePassword() {
        given()
            .contentType(ContentType.JSON)
            .body(ChangePasswordRequest(DUMMY_USER_PASSWORD, "password456"))
            .When()
            .post("/user/changePassword")
            .then()
            .statusCode(200)
        assertThat(login(Environment.auth.defaultUserName, DUMMY_USER_PASSWORD)).isEqualTo(401)
        assertThat(login(Environment.auth.defaultUserName, "password456")).isEqualTo(200)
        // only the caller's own password changes
        assertThat(login("user1", DUMMY_USER_PASSWORD)).isEqualTo(200)
    }

    @Test
    fun testChangePasswordInvalidOldPassword() {
        given()
            .contentType(ContentType.JSON)
            .body(ChangePasswordRequest("invalid", "password456"))
            .When()
            .post("/user/changePassword")
            .then()
            .statusCode(400)
    }

    @Test
    fun testChangePasswordTooShort() {
        given()
            .contentType(ContentType.JSON)
            .body(ChangePasswordRequest(DUMMY_USER_PASSWORD, "short"))
            .When()
            .post("/user/changePassword")
            .then()
            .statusCode(400)
        assertThat(login(Environment.auth.defaultUserName, DUMMY_USER_PASSWORD)).isEqualTo(200)
    }

    @Test
    fun testUpdateUser() {
        val original = get("/user")
            .then()
            .statusCode(200)
            .extract().to<User>()
        assertThat(original.displayName).isNull()
        assertThat(original.digest).isFalse()
        val updated = given()
            .contentType(ContentType.JSON)
            .body(UserUpdateRequest("Bart Smith", true))
            .When()
            .put("/user")
            .then()
            .statusCode(200)
            .extract().to<User>()
        assertThat(updated.id).isEqualTo(original.id)
        assertThat(updated.username).isEqualTo(original.username)
        assertThat(updated.displayName).isEqualTo("Bart Smith")
        assertThat(updated.digest).isTrue()
        assertThat(updated.dateCreated).isNotEqualTo(updated.dateUpdated)
        val user = get("/user")
            .then()
            .statusCode(200)
            .extract().to<User>()
        assertThat(user).isEqualTo(updated)
    }

    @Test
    fun testJoltToken() {
        val updated = given()
            .contentType(ContentType.JSON)
            .body(JoltTokenRequest("jlt_live_abc"))
            .When()
            .put("/user/jolt")
            .then()
            .statusCode(200)
            .body("joltToken", Matchers.nullValue())
            .extract().to<User>()
        assertThat(updated.joltConfigured).isTrue()
        get("/user").then().statusCode(200).body("joltConfigured", Matchers.equalTo(true))

        given().contentType(ContentType.JSON).body(JoltTokenRequest("not/safe"))
            .When().put("/user/jolt").then().statusCode(400)
        given().contentType(ContentType.JSON).body(JoltTokenRequest(null))
            .When().put("/user/jolt").then().statusCode(200).body("joltConfigured", Matchers.equalTo(false))
    }

    @Test
    fun testGetUserActivityLog() {
        createDummyEntry("e1", "note1", "note content", EntryType.NOTE)
        createDummyEntry("e2", "note2", "note content", EntryType.NOTE, userId = createDummyUser("user2"))
        post("/entry/{id}/star", "e1")
        post("/entry/{id}/unstar", "e1")
        EntryAuditService().acceptAuditEvent(EntryId("e2"), "source", "not mine")

        val activityLog = get("/user/activity")
            .then()
            .statusCode(200)
            .extract().to<Page<ActivityLogItem>>()

        assertThat(activityLog.total).isEqualTo(2)
        assertThat(activityLog.page).isEqualTo(1)
        assertThat(activityLog.content).hasSize(2)
        assertThat(activityLog.content).extracting("id").doesNotHaveDuplicates()
        assertThat(activityLog.content).extracting<EntryId> { it.entryId }
            .containsOnly(EntryId("e1"))
        assertThat(activityLog.content).extracting("details").doesNotHaveDuplicates()
        assertThat(activityLog.content).extracting("entryType").containsOnly(EntryType.NOTE)
        assertThat(activityLog.content).extracting("entryTitle").containsOnly("note1")
        assertThat(activityLog.content).extracting("timestamp").doesNotContainNull()
    }

}
