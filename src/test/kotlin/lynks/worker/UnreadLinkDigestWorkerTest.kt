package lynks.worker

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import lynks.common.DatabaseTest
import lynks.common.DigestId
import lynks.common.UserId
import lynks.digest.Digest
import lynks.digest.DigestService
import lynks.notify.NotifyService
import lynks.user.UserService
import lynks.util.TEST_USER
import lynks.util.createDummyUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
class UnreadLinkDigestWorkerTest: DatabaseTest() {

    private val userService = UserService(mockk())
    private val digestService = mockk<DigestService>()
    private val notifyService = mockk<NotifyService>(relaxed = true)
    private val user1 = UserId("user1-id")
    private val digest = Digest(DigestId("d1"), emptyList(), Instant.EPOCH)

    @BeforeEach
    fun setup() {
        every { digestService.generate(any()) } returns digest
    }

    private fun TestScope.runWorkerForAWeek() {
        val worker = UnreadLinkDigestWorker(notifyService, digestService, userService)
            .apply { runner = this@runWorkerForAWeek.coroutineContext }
        val send = worker.worker()
        runCurrent()
        advanceTimeBy(TimeUnit.DAYS.toMillis(8))
        send.close()
        worker.cancel()
    }

    @Test
    fun testNotEnabled() = runTest {
        createDummyUser("user1", digest = false, id = user1)
        runWorkerForAWeek()
        coVerify { digestService.generate(user1) }
        coVerify(exactly = 0) { notifyService.create(any(), any()) }
    }

    @Test
    fun testCreateFromStartup() = runTest {
        createDummyUser("user1", digest = true, id = user1)
        runWorkerForAWeek()
        // every active user gets a digest, only those who asked are notified
        coVerify { digestService.generate(TEST_USER) }
        coVerify { digestService.generate(user1) }
        coVerify { notifyService.create(user1, any()) }
        coVerify(exactly = 0) { notifyService.create(TEST_USER, any()) }
    }

    @Test
    fun testNoUnreadLinksNoNotification() = runTest {
        createDummyUser("user1", digest = true, id = user1)
        every { digestService.generate(any()) } returns null
        runWorkerForAWeek()
        coVerify(exactly = 0) { notifyService.create(any(), any()) }
    }

    @Test
    fun testInactiveUserSkipped() = runTest {
        createDummyUser("user1", digest = true, id = user1, activated = false)
        runWorkerForAWeek()
        coVerify(exactly = 0) { digestService.generate(user1) }
        coVerify(exactly = 0) { notifyService.create(any(), any()) }
    }

    @Test
    fun testOneUserFailingDoesNotStopOthers() = runTest {
        createDummyUser("user1", digest = true, id = user1)
        every { digestService.generate(TEST_USER) } throws IllegalStateException("failed")
        runWorkerForAWeek()
        coVerify { notifyService.create(user1, any()) }
    }

    @Test
    fun testNextFireIsFollowingMondayMorning() {
        val worker = UnreadLinkDigestWorker(notifyService, digestService, userService)
        // 2026-09-21 is a Monday
        assertThat(worker.untilNextFire(LocalDateTime.of(2026, 9, 21, 8, 0))).isEqualTo(Duration.ofHours(1))
        assertThat(worker.untilNextFire(LocalDateTime.of(2026, 9, 21, 9, 0))).isEqualTo(Duration.ofDays(7))
        assertThat(worker.untilNextFire(LocalDateTime.of(2026, 9, 20, 9, 0))).isEqualTo(Duration.ofDays(1))
    }

}
