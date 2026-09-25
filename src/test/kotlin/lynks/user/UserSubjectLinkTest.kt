package lynks.user

import io.mockk.mockk
import lynks.common.DatabaseTest
import lynks.common.UserId
import lynks.util.activateUser
import lynks.util.createDummyUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UserSubjectLinkTest : DatabaseTest() {

    private val userService = UserService(mockk<TwoFactorService>())
    private val user2 = UserId("user2-id")

    @BeforeEach
    fun setup() {
        createDummyUser("user2", id = user2)
    }

    @Test
    fun testLinkByUsernameAndFind() {
        assertThat(userService.findBySubject("sub-1")).isNull()
        assertThat(userService.linkSubjectByUsername("user2", "sub-1")).isEqualTo(user2)
        assertThat(userService.findBySubject("sub-1")).isEqualTo(SubjectOwner(user2, true))
    }

    @Test
    fun testLinkByUsernameIsExactAndCaseSensitive() {
        assertThat(userService.linkSubjectByUsername("USER2", "sub-1")).isNull()
        assertThat(userService.linkSubjectByUsername("user", "sub-1")).isNull()
        assertThat(userService.findBySubject("sub-1")).isNull()
    }

    @Test
    fun testLinkByUsernameNeverReplacesALink() {
        userService.linkSubjectByUsername("user2", "sub-1")
        assertThat(userService.linkSubjectByUsername("user2", "sub-2")).isNull()
        assertThat(userService.findBySubject("sub-1")?.id).isEqualTo(user2)
        assertThat(userService.findBySubject("sub-2")).isNull()
    }

    @Test
    fun testLinkByUsernameSkipsDeactivatedUser() {
        activateUser("user2", false)
        assertThat(userService.linkSubjectByUsername("user2", "sub-1")).isNull()
    }

    @Test
    fun testFindReportsDeactivatedOwner() {
        userService.linkSubjectByUsername("user2", "sub-1")
        activateUser("user2", false)
        assertThat(userService.findBySubject("sub-1")).isEqualTo(SubjectOwner(user2, false))
    }
}
