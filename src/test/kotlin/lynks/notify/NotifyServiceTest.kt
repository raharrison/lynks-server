package lynks.notify

import io.ktor.websocket.*
import io.mockk.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import lynks.common.DatabaseTest
import lynks.common.EntryId
import lynks.common.EntryType
import lynks.common.NotificationId
import lynks.common.page.PageRequest
import lynks.common.page.SortDirection
import lynks.notify.NewNotification.Companion.discussions
import lynks.notify.NewNotification.Companion.error
import lynks.notify.NewNotification.Companion.processed
import lynks.notify.NewNotification.Companion.reminder
import lynks.notify.jolt.JoltClient
import lynks.util.createDummyEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@DelicateCoroutinesApi
class NotifyServiceTest: DatabaseTest() {

    private val joltClient = mockk<JoltClient>()
    private val notifyService = NotifyService(joltClient)

    @BeforeEach
    fun setup() {
        createDummyEntry("e1", "title", "content", EntryType.NOTE)
        createDummyEntry("e2", "title2", "content2", EntryType.LINK)
    }

    @Test
    fun testGetNotification() = runBlocking {
        val notification = notifyService.create(reminder("elapsed", EntryId("e1")), false)
        assertThat(notification.type).isEqualTo(NotificationType.REMINDER)
        assertThat(notification.message).isEqualTo("elapsed")
        assertThat(notification.read).isFalse()
        assertThat(notification.entryId).isEqualTo(EntryId("e1"))
        assertThat(notification.entryTitle).isEqualTo("title")
        assertThat(notification.entryType).isEqualTo(EntryType.NOTE)
        val retrieved = notifyService.getNotification(notification.id)
        assertThat(retrieved).isEqualTo(notification)
        Unit
    }

    @Test
    fun testGetNotificationNotFound() = runBlocking {
        val notification = notifyService.getNotification(NotificationId("notfound"))
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
        assertThat(notifications.content).extracting<NotificationId> { it.id }
            .containsExactly(reminder.id)

        notifications = notifyService.getNotifications(PageRequest(2, 1))
        assertThat(notifications.content).hasSize(1)
        assertThat(notifications.page).isEqualTo(2L)
        assertThat(notifications.size).isEqualTo(1)
        assertThat(notifications.total).isEqualTo(3)
        assertThat(notifications.content).extracting<NotificationId> { it.id }
            .containsExactly(discussions.id)

        notifications = notifyService.getNotifications(PageRequest(1, 3))
        assertThat(notifications.content).hasSize(3)
        assertThat(notifications.page).isEqualTo(1L)
        assertThat(notifications.size).isEqualTo(3)
        assertThat(notifications.total).isEqualTo(3)
        assertThat(notifications.content).extracting<NotificationId> { it.id }
            .containsExactly(reminder.id, discussions.id, processed.id)

        notifications = notifyService.getNotifications(PageRequest(1, 10))
        assertThat(notifications.content).hasSize(3)
        assertThat(notifications.page).isEqualTo(1L)
        assertThat(notifications.size).isEqualTo(10)
        assertThat(notifications.total).isEqualTo(3)
        assertThat(notifications.content).extracting<NotificationId> { it.id }
            .doesNotHaveDuplicates()
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
        assertThat(notifications.content).extracting<NotificationId> { it.id }
            .containsExactly(reminder.id, discussions.id, processed.id)
        assertThat(notifications.page).isEqualTo(1L)
        assertThat(notifications.size).isEqualTo(10)
        assertThat(notifications.total).isEqualTo(3)

        notifications = notifyService.getNotifications(PageRequest(1, 10, sort = "dateCreated", direction = SortDirection.ASC))
        assertThat(notifications.content).extracting<NotificationId> { it.id }
            .containsExactly(processed.id, discussions.id, reminder.id)
        assertThat(notifications.page).isEqualTo(1L)
        assertThat(notifications.size).isEqualTo(10)
        assertThat(notifications.total).isEqualTo(3)
        Unit
    }

    @Test
    fun testGetNotificationsSortByRead() = runBlocking {
        val processed = notifyService.create(processed(), false)
        delay(10)
        val discussions = notifyService.create(discussions(), false)
        delay(10)
        val reminder = notifyService.create(reminder(), false)

        notifyService.read(reminder.id, true)

        val notifications = notifyService.getNotifications(PageRequest(sort = "read", direction = SortDirection.ASC))
        assertThat(notifications.content).hasSize(3)
        assertThat(notifications.total).isEqualTo(3)
        assertThat(notifications.content).extracting<NotificationId> { it.id }
            .containsExactly(discussions.id, processed.id, reminder.id)
        Unit
    }

    @Test
    fun testGetUnreadCount() = runBlocking {
        val processed = notifyService.create(processed(), false)
        notifyService.create(discussions(), false)
        assertThat(notifyService.getUnreadCount()).isEqualTo(2)
        notifyService.read(processed.id, true)
        assertThat(notifyService.getUnreadCount()).isOne()
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
        val notification = notifyService.create(discussions("found", EntryId("e2")), false)
        assertThat(notification.type).isEqualTo(NotificationType.DISCUSSIONS)
        assertThat(notification.message).isEqualTo("found")
        assertThat(notification.read).isFalse()
        assertThat(notification.entryId).isEqualTo(EntryId("e2"))
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
        notifyService.create(error("error", EntryId("e2")), true)
        coVerify(exactly = 1) { channel.send(any()) }
    }

    @Test
    fun testReadNotificationSuccess() = runBlocking {
        val notification = notifyService.create(error("error", EntryId("e2")), false)
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
        val updated = notifyService.read(NotificationId("notfound"), true)
        assertThat(updated).isZero()
        Unit
    }

    @Test
    fun testMarkAllRead() = runBlocking {
        notifyService.create(processed(), false)
        notifyService.create(discussions(), false)
        notifyService.create(reminder(), false)
        assertThat(notifyService.getUnreadCount()).isEqualTo(3)
        assertThat(notifyService.markAllRead()).isEqualTo(3)
        assertThat(notifyService.getUnreadCount()).isZero()
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
    fun testSendJoltNotification() = runBlocking {
        val notification = notifyService.create(processed("success"), false)
        coEvery { joltClient.sendNotification(any(), notification.message, any()) } just Runs
        notifyService.sendJoltNotification(notification, "title")
        coVerify(exactly = 1) { joltClient.sendNotification("title", notification.message, any()) }
    }

}
