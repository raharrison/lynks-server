package worker

import common.Link
import entry.EntryAuditService
import entry.LinkService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineContext
import notify.NotifyService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import user.Preferences
import user.UserService
import java.util.concurrent.TimeUnit

class UnreadLinkDigestWorkerTest {

    private val userService = mockk<UserService>()
    private val linkService = mockk<LinkService>()
    private val notifyService = mockk<NotifyService>(relaxUnitFun = true)
    private val entryAuditService = mockk<EntryAuditService>(relaxUnitFun = true)

    private val context = TestCoroutineContext()

    @BeforeEach
    fun before() {
        every { linkService.getUnread() } returns listOf(Link("id", "title", "url", "src", "", 0, 0))
    }

    @Test
    fun testNotEnabled() = runBlocking(context) {
        every { userService.currentUserPreferences } returns Preferences(digest = false)
        val worker = UnreadLinkDigestWorker(linkService, userService, notifyService, entryAuditService)
                .apply { runner = context }.worker()
        worker.close()

        context.triggerActions()
        verify(exactly = 0) { notifyService.sendEmail(any(), any()) }
    }

    @Test
    fun testCreateFromStartup() = runBlocking(context) {
        every { userService.currentUserPreferences } returns Preferences(digest = true)
        val worker = UnreadLinkDigestWorker(linkService, userService, notifyService, entryAuditService)
                .apply { runner = context }.worker()
        context.triggerActions() // to get to initial delay

        context.advanceTimeBy(7, TimeUnit.DAYS)

        worker.close()
        verify { linkService.getUnread() }
        verify { notifyService.sendEmail(any(), any()) }
    }

    @Test
    fun testOverrideWithNewPreferences()= runBlocking(context) {
        every { userService.currentUserPreferences } returns Preferences(digest = true)
        val worker = UnreadLinkDigestWorker(linkService, userService, notifyService, entryAuditService)
                .apply { runner = context }.worker()
        context.triggerActions() // to get to initial delay

        worker.send(UnreadLinkDigestWorkerRequest(Preferences(digest = false)))
        context.triggerActions()

        context.advanceTimeBy(7, TimeUnit.DAYS)

        worker.close()
        verify(exactly = 0) { notifyService.sendEmail(any(), any()) }
    }

}