package lynks.notify

import io.ktor.http.cio.websocket.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import lynks.common.DatabaseTest
import lynks.common.EntryType
import lynks.common.page.PageRequest
import lynks.common.page.SortDirection
import lynks.notify.NewNotification.Companion.discussions
import lynks.notify.NewNotification.Companion.error
import lynks.notify.NewNotification.Companion.processed
import lynks.notify.NewNotification.Companion.reminder
import lynks.notify.pushover.PushoverClient
import lynks.user.UserService
import lynks.util.createDummyEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class NotifyServiceTest: DatabaseTest() {

    private val pushoverClient = mockk<PushoverClient>()
    private val notifyService = NotifyService(UserService(), pushoverClient)

    @BeforeEach
    fun setup() {
        createDummyEntry("e1", "title", "content", EntryType.NOTE)
        createDummyEntry("e2", "title2", "content2", EntryType.LINK)
    }

    @Test
    fun testGetNotification() = runBlocking {
        val notification = notifyService.create(reminder("elapsed", "e1"), false)
        assertThat(notification.type).isEqualTo(NotificationType.REMINDER)
        assertThat(notification.message).isEqualTo("elapsed")
        assertThat(notification.read).isFalse()
        assertThat(notification.entryId).isEqualTo("e1")
        assertThat(notification.entryTitle).isEqualTo("title")
        assertThat(notification.entryType).isEqualTo(EntryType.NOTE)
        val retrieved = notifyService.getNotification(notification.id)
        assertThat(retrieved).isEqualTo(notification)
        Unit
    }

    @Test
    fun testGetNotificationNotFound() = runBlocking {
        val notification = notifyService.getNotification("notfound")
        assertThat(notification).isNull()
    }

    @Test
    fun testGetNotificationsPaging() = runBlocking {
        val processed = notifyService.create(processed(), false)
        delay(10)
        val discussions = notifyService.create(discussions(), false)
        delay(10)
        val reminder = notifyService.create(reminder(), false)

        var notifications = notifyService.getNotifications(PageRequest(1, 1))
        assertThat(notifications.content).hasSize(1)
        assertThat(notifications.page).isEqualTo(1L)
        assertThat(notifications.size).isEqualTo(1)
        assertThat(notifications.total).isEqualTo(3)
        assertThat(notifications.content).extracting("id").containsExactly(reminder.id)

        notifications = notifyService.getNotifications(PageRequest(2, 1))
        assertThat(notifications.content).hasSize(1)
        assertThat(notifications.page).isEqualTo(2L)
        assertThat(notifications.size).isEqualTo(1)
        assertThat(notifications.total).isEqualTo(3)
        assertThat(notifications.content).extracting("id").containsExactly(discussions.id)

        notifications = notifyService.getNotifications(PageRequest(1, 3))
        assertThat(notifications.content).hasSize(3)
        assertThat(notifications.page).isEqualTo(1L)
        assertThat(notifications.size).isEqualTo(3)
        assertThat(notifications.total).isEqualTo(3)
        assertThat(notifications.content).extracting("id").containsExactly(reminder.id, discussions.id, processed.id)

        notifications = notifyService.getNotifications(PageRequest(1, 10))
        assertThat(notifications.content).hasSize(3)
        assertThat(notifications.page).isEqualTo(1L)
        assertThat(notifications.size).isEqualTo(10)
        assertThat(notifications.total).isEqualTo(3)
        assertThat(notifications.content).extracting("id").doesNotHaveDuplicates()
        Unit
    }

    @Test
    fun testGetNotificationsSorting() = runBlocking {
        val processed = notifyService.create(processed(), false)
        delay(10)
        val discussions = notifyService.create(discussions(), false)
        delay(10)
        val reminder = notifyService.create(reminder(), false)

        var notifications = notifyService.getNotifications(PageRequest(1, 10, sort = "dateCreated", direction = SortDirection.DESC))
        assertThat(notifications.content).extracting("id").containsExactly(reminder.id, discussions.id, processed.id)
        assertThat(notifications.page).isEqualTo(1L)
        assertThat(notifications.size).isEqualTo(10)
        assertThat(notifications.total).isEqualTo(3)

        notifications = notifyService.getNotifications(PageRequest(1, 10, sort = "dateCreated", direction = SortDirection.ASC))
        assertThat(notifications.content).extracting("id").containsExactly(processed.id, discussions.id, reminder.id)
        assertThat(notifications.page).isEqualTo(1L)
        assertThat(notifications.size).isEqualTo(10)
        assertThat(notifications.total).isEqualTo(3)
        Unit
    }

    @Test
    fun testCreateNotificationNoEntry() = runBlocking {
        val notification = notifyService.create(processed("complete"), false)
        assertThat(notification.type).isEqualTo(NotificationType.PROCESSED)
        assertThat(notification.message).isEqualTo("complete")
        assertThat(notification.read).isFalse()
        assertThat(notification.entryId).isNull()
        assertThat(notification.entryTitle).isNull()
        assertThat(notification.entryType).isNull()
    }

    @Test
    fun testCreateNotificationWithEntry() = runBlocking {
        val notification = notifyService.create(discussions("found", "e2"), false)
        assertThat(notification.type).isEqualTo(NotificationType.DISCUSSIONS)
        assertThat(notification.message).isEqualTo("found")
        assertThat(notification.read).isFalse()
        assertThat(notification.entryId).isEqualTo("e2")
        assertThat(notification.entryTitle).isEqualTo("title2")
        assertThat(notification.entryType).isEqualTo(EntryType.LINK)
        Unit
    }

    @Test
    fun testCreateNotificationAndSendWeb() = runBlocking {
        val channel = mockk<SendChannel<Frame>>(relaxUnitFun = true)
        every { channel.isClosedForSend } returns false
        coEvery { channel.send(any()) } just Runs

        notifyService.join(channel)
        notifyService.create(error("error", "e2"), true)
        coVerify(exactly = 1) { channel.send(any()) }
    }

    @Test
    fun testReadNotificationSuccess() = runBlocking {
        val notification = notifyService.create(error("error", "e2"), false)
        assertThat(notification.read).isFalse()

        val readUpdate = notifyService.read(notification.id, true)
        assertThat(readUpdate).isOne()
        val updated = notifyService.getNotification(notification.id)
        assertThat(updated?.read).isTrue()

        val unreadUpdate = notifyService.read(notification.id, false)
        assertThat(unreadUpdate).isOne()
        val updated2 = notifyService.getNotification(notification.id)
        assertThat(updated2?.read).isFalse()
        Unit
    }

    @Test
    fun testReadNotificationNotFound() = runBlocking {
        val updated = notifyService.read("notfound", true)
        assertThat(updated).isZero()
        Unit
    }

    @Test
    fun testSendWebNotificationToOpen() = runBlocking {
        val channel = mockk<SendChannel<Frame>>(relaxUnitFun = true)
        every { channel.isClosedForSend } returns false
        coEvery { channel.send(any()) } just Runs

        val channel2 = mockk<SendChannel<Frame>>(relaxUnitFun = true)
        every { channel2.isClosedForSend } returns false
        coEvery { channel2.send(any()) } just Runs

        val processed = notifyService.create(processed(), false)
        notifyService.join(channel)
        notifyService.join(channel2)

        notifyService.sendWebNotification(processed)

        coVerify(exactly = 1) { channel.send(any()) }
        coVerify(exactly = 1) { channel2.send(any()) }
    }

    @Test
    fun testLeave() = runBlocking {
        val channel = mockk<SendChannel<Frame>>(relaxUnitFun = true)
        every { channel.isClosedForSend } returns false

        val processed = notifyService.create(processed(), false)
        notifyService.join(channel)
        notifyService.leave(channel)
        notifyService.sendWebNotification(processed)

        coVerify(exactly = 0) { channel.send(any()) }
    }

    @Test
    fun testRemoveClosed() = runBlocking {
        val channel = mockk<SendChannel<Frame>>(relaxUnitFun = true)
        every { channel.isClosedForSend } returns false
        coEvery { channel.send(any()) } just Runs

        val channel2 = mockk<SendChannel<Frame>>(relaxUnitFun = true)
        coEvery { channel2.send(any()) } just Runs
        every { channel2.isClosedForSend } returns true

        val processed = notifyService.create(processed(), false)
        notifyService.join(channel)
        notifyService.join(channel2)
        notifyService.sendWebNotification(processed)

        coVerify(exactly = 1) { channel.send(any()) }
        coVerify(exactly = 0) { channel2.send(any()) }
    }

    @Test
    fun testSendPushoverNotification() = runBlocking {
        val notification = notifyService.create(processed("success"), false)
        coEvery { pushoverClient.sendNotification(any(), notification.message) } just Runs
        notifyService.sendPushoverNotification(notification, "title")
        coVerify(exactly = 1) { pushoverClient.sendNotification("title", notification.message) }
    }

}
