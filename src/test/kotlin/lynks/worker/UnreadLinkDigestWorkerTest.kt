package lynks.worker

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import lynks.common.DatabaseTest
import lynks.common.DigestId
import lynks.digest.Digest
import lynks.digest.DigestService
import lynks.notify.NotifyService
import lynks.user.UserService
import lynks.util.createDummyUser
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
class UnreadLinkDigestWorkerTest: DatabaseTest() {

    private val userService = UserService(mockk())
    private val digestService = mockk<DigestService>()
    private val notifyService = mockk<NotifyService>(relaxUnitFun = true)

    @BeforeEach
    fun setup() {
        every { digestService.generate() } returns Digest(DigestId("d1"), emptyList(), Instant.EPOCH)
    }

    @Test
    fun testNotEnabled() = runTest {
        createDummyUser("user1", digest = false)
        val worker = UnreadLinkDigestWorker(notifyService, digestService, userService)
                .apply { runner = this@runTest.coroutineContext }
        val send = worker.worker()
        runCurrent()
        coVerify(exactly = 0) { notifyService.create(any(), any()) }
        send.close()
        worker.cancel()
    }

    @Test
    fun testCreateFromStartup() = runTest {
        createDummyUser("user1", digest = true)
        val worker = UnreadLinkDigestWorker(notifyService, digestService, userService)
                .apply { runner = this@runTest.coroutineContext }
        val send = worker.worker()
        runCurrent()

        advanceTimeBy(TimeUnit.DAYS.toMillis(8))

        coVerify { digestService.generate() }
        coVerify { notifyService.create(any(), any()) }
        send.close()
        worker.cancel()
    }

    @Test
    fun testNoUnreadLinksNoNotification() = runTest {
        createDummyUser("user1", digest = true)
        every { digestService.generate() } returns null
        val worker = UnreadLinkDigestWorker(notifyService, digestService, userService)
            .apply { runner = this@runTest.coroutineContext }
        val send = worker.worker()
        runCurrent()

        advanceTimeBy(TimeUnit.DAYS.toMillis(8))

        coVerify(exactly = 0) { notifyService.create(any(), any()) }
        send.close()
        worker.cancel()
    }

}
