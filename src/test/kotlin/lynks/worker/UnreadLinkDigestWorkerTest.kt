package lynks.worker

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import lynks.common.Link
import lynks.entry.EntryAuditService
import lynks.entry.LinkService
import lynks.notify.NotifyService
import lynks.user.Preferences
import lynks.user.UserService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
class UnreadLinkDigestWorkerTest {

    private val userService = mockk<UserService>()
    private val linkService = mockk<LinkService>()
    private val notifyService = mockk<NotifyService>(relaxUnitFun = true)
    private val entryAuditService = mockk<EntryAuditService>(relaxUnitFun = true)

    @BeforeEach
    fun before() {
        every { linkService.getUnread() } returns listOf(Link("id", "title", "url", "src", "", 0, 0))
    }

    @Test
    fun testNotEnabled() = runTest {
        every { userService.currentUserPreferences } returns Preferences(digest = false)
        val worker = UnreadLinkDigestWorker(linkService, userService, notifyService, entryAuditService)
                .apply { runner = this@runTest.coroutineContext }
        val send = worker.worker()
        runCurrent()
        verify(exactly = 0) { notifyService.sendEmail(any(), any()) }
        send.close()
        worker.cancelAll()
    }

    @Test
    fun testCreateFromStartup() = runTest {
        every { userService.currentUserPreferences } returns Preferences(digest = true)
        val worker = UnreadLinkDigestWorker(linkService, userService, notifyService, entryAuditService)
                .apply { runner = this@runTest.coroutineContext }
        val send = worker.worker()
        runCurrent()

        advanceTimeBy(TimeUnit.DAYS.toMillis(8))

        verify { linkService.getUnread() }
        verify { notifyService.sendEmail(any(), any()) }
        send.close()
        worker.cancelAll()
    }

    @Test
    fun testOverrideWithNewPreferences()= runTest {
        every { userService.currentUserPreferences } returns Preferences(digest = true)
        val worker = UnreadLinkDigestWorker(linkService, userService, notifyService, entryAuditService)
                .apply { runner = this@runTest.coroutineContext }
        val send = worker.worker()
        runCurrent()

        send.send(UnreadLinkDigestWorkerRequest(Preferences(digest = false)))

        advanceTimeBy(TimeUnit.DAYS.toMillis(7))

        verify(exactly = 0) { notifyService.sendEmail(any(), any()) }
        send.close()
        worker.cancelAll()
    }

}
