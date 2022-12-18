package lynks.worker

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import lynks.common.DatabaseTest
import lynks.common.Link
import lynks.entry.LinkService
import lynks.notify.NotifyService
import lynks.user.UserService
import lynks.util.createDummyUser
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
class UnreadLinkDigestWorkerTest: DatabaseTest() {

    private val userService = UserService(mockk())
    private val linkService = mockk<LinkService>()
    private val notifyService = mockk<NotifyService>(relaxUnitFun = true)

    @BeforeEach
    fun setup() {
        every { linkService.getUnread() } returns listOf(Link("id", "title", "url", "src", "", 0, 0))
    }

    @Test
    fun testNotEnabled() = runTest {
        createDummyUser("user1", email = "default@mail.com", digest = false)
        val worker = UnreadLinkDigestWorker(notifyService, linkService, userService)
                .apply { runner = this@runTest.coroutineContext }
        val send = worker.worker()
        runCurrent()
        verify(exactly = 0) { notifyService.sendEmail(any(), any(), any()) }
        send.close()
        worker.cancel()
    }

    @Test
    fun testCreateFromStartup() = runTest {
        createDummyUser("user1", email = "default@mail.com", digest = true)
        val worker = UnreadLinkDigestWorker(notifyService, linkService, userService)
                .apply { runner = this@runTest.coroutineContext }
        val send = worker.worker()
        runCurrent()

        advanceTimeBy(TimeUnit.DAYS.toMillis(8))

        verify { linkService.getUnread() }
        verify { notifyService.sendEmail("default@mail.com", any(), any()) }
        send.close()
        worker.cancel()
    }

}
