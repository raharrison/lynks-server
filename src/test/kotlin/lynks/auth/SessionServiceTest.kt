package lynks.auth

import lynks.common.DatabaseTest
import lynks.common.Environment
import lynks.common.UserId
import lynks.util.HashUtils
import lynks.util.TEST_USER
import lynks.util.activateUser
import lynks.util.createDummyUser
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.*

class SessionServiceTest : DatabaseTest() {

    private class MutableClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?) = this
    }

    private val clock = MutableClock(Instant.now())
    private val sessionService = SessionService(Environment.AuthSession(idleDays = 30, maxDays = 90), clock)
    private val user2 = UserId("user2-id")

    @BeforeEach
    fun setup() {
        createDummyUser("user2", id = user2)
    }

    private fun create(userId: UserId = TEST_USER) = sessionService.create(userId, SessionMethod.PASSWORD, "agent", "127.0.0.1")

    private fun row(id: String) = transaction { UserSessions.selectAll().where { UserSessions.id eq id }.single() }

    @Test
    fun testCreateAndAuthenticate() {
        val session = create()
        val principal = sessionService.authenticate(session.token)
        assertThat(principal?.id).isEqualTo(TEST_USER)
        assertThat(principal?.username).isEqualTo("test-user")
        assertThat(principal?.sessionId).isEqualTo(session.id)
        assertThat(session.token).hasSizeGreaterThanOrEqualTo(43)
    }

    @Test
    fun testOnlyTheHashIsStored() {
        val session = create()
        val stored = row(session.id)[UserSessions.tokenHash]
        assertThat(stored).isEqualTo(HashUtils.sha256Hash(session.token)).isNotEqualTo(session.token)
    }

    @Test
    fun testUnknownTokenIsRejected() {
        create()
        assertThat(sessionService.authenticate("not-a-token")).isNull()
    }

    @Test
    fun testDeactivatedUserIsRejected() {
        val session = create(user2)
        activateUser("user2", false)
        assertThat(sessionService.authenticate(session.token)).isNull()
        activateUser("user2", true)
        assertThat(sessionService.authenticate(session.token)).isNotNull()
    }

    @Test
    fun testIdleExpiry() {
        val session = create()
        clock.now = clock.now.plus(Duration.ofDays(30))
        assertThat(sessionService.authenticate(session.token)).isNull()
    }

    @Test
    fun testActivitySlidesIdleExpiry() {
        val session = create()
        clock.now = clock.now.plus(Duration.ofDays(20))
        assertThat(sessionService.authenticate(session.token)).isNotNull()
        clock.now = clock.now.plus(Duration.ofDays(20))
        assertThat(sessionService.authenticate(session.token)).isNotNull()
    }

    @Test
    fun testRecentActivityIsNotWrittenAgain() {
        val session = create()
        val before = row(session.id)[UserSessions.lastSeen]
        clock.now = clock.now.plus(Duration.ofMinutes(4))
        sessionService.authenticate(session.token)
        assertThat(row(session.id)[UserSessions.lastSeen]).isEqualTo(before)
        clock.now = clock.now.plus(Duration.ofMinutes(2))
        sessionService.authenticate(session.token)
        assertThat(row(session.id)[UserSessions.lastSeen].toInstant()).isAfter(before.toInstant())
    }

    @Test
    fun testAbsoluteExpiryDespiteActivity() {
        val session = create()
        repeat(4) {
            clock.now = clock.now.plus(Duration.ofDays(20))
            assertThat(sessionService.authenticate(session.token)).isNotNull()
        }
        assertThat(row(session.id)[UserSessions.expiresAt]).isEqualTo(row(session.id)[UserSessions.maxExpiresAt])
        clock.now = clock.now.plus(Duration.ofDays(10))
        assertThat(sessionService.authenticate(session.token)).isNull()
    }

    @Test
    fun testList() {
        val first = create()
        clock.now = clock.now.plusSeconds(60)
        val second = create()
        create(user2)
        val sessions = sessionService.list(TEST_USER, second.id)
        assertThat(sessions).extracting("id").containsExactly(second.id, first.id)
        assertThat(sessions).extracting("current").containsExactly(true, false)
        assertThat(sessions[0].method).isEqualTo(SessionMethod.PASSWORD)
        assertThat(sessions[0].userAgent).isEqualTo("agent")
        assertThat(sessions[0].ip).isEqualTo("127.0.0.1")
    }

    @Test
    fun testRevokeOnlyOwnSession() {
        val mine = create()
        val theirs = create(user2)
        assertThat(sessionService.revoke(TEST_USER, theirs.id)).isFalse()
        assertThat(sessionService.authenticate(theirs.token)).isNotNull()
        assertThat(sessionService.revoke(TEST_USER, mine.id)).isTrue()
        assertThat(sessionService.authenticate(mine.token)).isNull()
    }

    @Test
    fun testRevokeOthersKeepsCurrent() {
        val current = create()
        val other = create()
        val theirs = create(user2)
        assertThat(sessionService.revokeOthers(TEST_USER, current.id)).isEqualTo(1)
        assertThat(sessionService.authenticate(current.token)).isNotNull()
        assertThat(sessionService.authenticate(other.token)).isNull()
        assertThat(sessionService.authenticate(theirs.token)).isNotNull()
        assertThat(sessionService.revokeOthers(TEST_USER, null)).isEqualTo(1)
    }

    @Test
    fun testDelete() {
        val session = create()
        assertThat(sessionService.delete(session.token)).isTrue()
        assertThat(sessionService.authenticate(session.token)).isNull()
        assertThat(sessionService.delete(session.token)).isFalse()
    }

    @Test
    fun testDeleteExpired() {
        create()
        clock.now = clock.now.plus(Duration.ofDays(31))
        val fresh = create()
        assertThat(sessionService.deleteExpired()).isEqualTo(1)
        assertThat(sessionService.authenticate(fresh.token)).isNotNull()
    }

    @Test
    fun testDeletingUserDeletesSessions() {
        val session = create(user2)
        transaction { lynks.user.Users.deleteWhere { lynks.user.Users.id eq user2.value } }
        assertThat(transaction { UserSessions.selectAll().where { UserSessions.id eq session.id }.count() }).isZero()
    }
}
