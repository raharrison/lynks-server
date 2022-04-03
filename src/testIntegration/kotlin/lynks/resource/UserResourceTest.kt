package lynks.resource

import io.restassured.RestAssured.get
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import lynks.common.ServerTest
import lynks.user.AuthRequest
import lynks.user.ChangePasswordRequest
import lynks.user.User
import lynks.util.createDummyUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UserResourceTest : ServerTest() {

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
        val updated = given()
            .contentType(ContentType.JSON)
            .body(User(original.username, "updated@mail.com", "Bart Smith", true))
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
            .body(User("invalid", "updated@mail.com", "Bill Smith"))
            .When()
            .put("/user")
            .then()
            .statusCode(404)
    }

    @Test
    fun testUpdateUserInvalidEmail() {
        given()
            .contentType(ContentType.JSON)
            .body(User("user1", "invalid"))
            .When()
            .put("/user")
            .then()
            .statusCode(400)
    }


}
