package lynks.notify

import io.mockk.*
import kotlinx.coroutines.DelicateCoroutinesApi
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
import lynks.user.UserService
import lynks.util.OTHER_USER
import lynks.util.TEST_USER
import lynks.util.createDummyEntry
import lynks.util.createDummyUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@DelicateCoroutinesApi
class NotifyServiceTest: DatabaseTest() {

    private val joltClient = mockk<JoltClient>()
    private val userService = UserService(mockk())
    private val notifyService = NotifyService(joltClient, userService)

    @BeforeEach
    fun setup() {
        createDummyEntry("e1", "title", "content", EntryType.NOTE)
        createDummyEntry("e2", "title2", "content2", EntryType.LINK)
    }

    @Test
    fun testGetNotification() = runBlocking {
        val notification = notifyService.create(TEST_USER, reminder("elapsed", EntryId("e1")))
        assertThat(notification.type).isEqualTo(NotificationType.REMINDER)
        assertThat(notification.message).isEqualTo("elapsed")
        assertThat(notification.read).isFalse()
        assertThat(notification.entryId).isEqualTo(EntryId("e1"))
        assertThat(notification.entryTitle).isEqualTo("title")
        assertThat(notification.entryType).isEqualTo(EntryType.NOTE)
        val retrieved = notifyService.getNotification(TEST_USER, notification.id)
        assertThat(retrieved).isEqualTo(notification)
        Unit
    }

    @Test
    fun testGetNotificationNotFound() = runBlocking {
        val notification = notifyService.getNotification(TEST_USER, NotificationId("notfound"))
        assertThat(notification).isNull()
    }

    @Test
    fun testGetNotificationsPaging() = runBlocking {
        val processed = notifyService.create(TEST_USER, processed())
        delay(10)
        val discussions = notifyService.create(TEST_USER, discussions())
        delay(10)
        val reminder = notifyService.create(TEST_USER, reminder())

        var notifications = notifyService.getNotifications(TEST_USER, PageRequest(1, 1))
        assertThat(notifications.content).hasSize(1)
        assertThat(notifications.page).isEqualTo(1L)
        assertThat(notifications.size).isEqualTo(1)
        assertThat(notifications.total).isEqualTo(3)
        assertThat(notifications.content).extracting<NotificationId> { it.id }
            .containsExactly(reminder.id)

        notifications = notifyService.getNotifications(TEST_USER, PageRequest(2, 1))
        assertThat(notifications.content).hasSize(1)
        assertThat(notifications.page).isEqualTo(2L)
        assertThat(notifications.size).isEqualTo(1)
        assertThat(notifications.total).isEqualTo(3)
        assertThat(notifications.content).extracting<NotificationId> { it.id }
            .containsExactly(discussions.id)

        notifications = notifyService.getNotifications(TEST_USER, PageRequest(1, 3))
        assertThat(notifications.content).hasSize(3)
        assertThat(notifications.page).isEqualTo(1L)
        assertThat(notifications.size).isEqualTo(3)
        assertThat(notifications.total).isEqualTo(3)
        assertThat(notifications.content).extracting<NotificationId> { it.id }
            .containsExactly(reminder.id, discussions.id, processed.id)

        notifications = notifyService.getNotifications(TEST_USER, PageRequest(1, 10))
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
        val processed = notifyService.create(TEST_USER, processed())
        delay(10)
        val discussions = notifyService.create(TEST_USER, discussions())
        delay(10)
        val reminder = notifyService.create(TEST_USER, reminder())

        var notifications =
            notifyService.getNotifications(TEST_USER, PageRequest(1, 10, sort = "dateCreated", direction = SortDirection.DESC))
        assertThat(notifications.content).extracting<NotificationId> { it.id }
            .containsExactly(reminder.id, discussions.id, processed.id)
        assertThat(notifications.page).isEqualTo(1L)
        assertThat(notifications.size).isEqualTo(10)
        assertThat(notifications.total).isEqualTo(3)

        notifications =
            notifyService.getNotifications(TEST_USER, PageRequest(1, 10, sort = "dateCreated", direction = SortDirection.ASC))
        assertThat(notifications.content).extracting<NotificationId> { it.id }
            .containsExactly(processed.id, discussions.id, reminder.id)
        assertThat(notifications.page).isEqualTo(1L)
        assertThat(notifications.size).isEqualTo(10)
        assertThat(notifications.total).isEqualTo(3)
        Unit
    }

    @Test
    fun testGetNotificationsSortByRead() = runBlocking {
        val processed = notifyService.create(TEST_USER, processed())
        delay(10)
        val discussions = notifyService.create(TEST_USER, discussions())
        delay(10)
        val reminder = notifyService.create(TEST_USER, reminder())

        notifyService.read(TEST_USER, reminder.id, true)

        val notifications = notifyService.getNotifications(TEST_USER, PageRequest(sort = "read", direction = SortDirection.ASC))
        assertThat(notifications.content).hasSize(3)
        assertThat(notifications.total).isEqualTo(3)
        assertThat(notifications.content).extracting<NotificationId> { it.id }
            .containsExactly(discussions.id, processed.id, reminder.id)
        Unit
    }

    @Test
    fun testGetUnreadCount() = runBlocking {
        val processed = notifyService.create(TEST_USER, processed())
        notifyService.create(TEST_USER, discussions())
        assertThat(notifyService.getUnreadCount(TEST_USER)).isEqualTo(2)
        notifyService.read(TEST_USER, processed.id, true)
        assertThat(notifyService.getUnreadCount(TEST_USER)).isOne()
        Unit
    }

    @Test
    fun testCreateNotificationNoEntry() = runBlocking {
        val notification = notifyService.create(TEST_USER, processed("complete"))
        assertThat(notification.type).isEqualTo(NotificationType.PROCESSED)
        assertThat(notification.message).isEqualTo("complete")
        assertThat(notification.read).isFalse()
        assertThat(notification.entryId).isNull()
        assertThat(notification.entryTitle).isNull()
        assertThat(notification.entryType).isNull()
    }

    @Test
    fun testCreateNotificationWithEntry() = runBlocking {
        val notification = notifyService.create(TEST_USER, discussions("found", EntryId("e2")))
        assertThat(notification.type).isEqualTo(NotificationType.DISCUSSIONS)
        assertThat(notification.message).isEqualTo("found")
        assertThat(notification.read).isFalse()
        assertThat(notification.entryId).isEqualTo(EntryId("e2"))
        assertThat(notification.entryTitle).isEqualTo("title2")
        assertThat(notification.entryType).isEqualTo(EntryType.LINK)
        Unit
    }

    @Test
    fun testReadNotificationSuccess() = runBlocking {
        val notification = notifyService.create(TEST_USER, error("error", EntryId("e2")))
        assertThat(notification.read).isFalse()

        val readUpdate = notifyService.read(TEST_USER, notification.id, true)
        assertThat(readUpdate).isOne()
        val updated = notifyService.getNotification(TEST_USER, notification.id)
        assertThat(updated?.read).isTrue()

        val unreadUpdate = notifyService.read(TEST_USER, notification.id, false)
        assertThat(unreadUpdate).isOne()
        val updated2 = notifyService.getNotification(TEST_USER, notification.id)
        assertThat(updated2?.read).isFalse()
        Unit
    }

    @Test
    fun testReadNotificationNotFound() = runBlocking {
        val updated = notifyService.read(TEST_USER, NotificationId("notfound"), true)
        assertThat(updated).isZero()
        Unit
    }

    @Test
    fun testMarkAllRead() = runBlocking {
        notifyService.create(TEST_USER, processed())
        notifyService.create(TEST_USER, discussions())
        notifyService.create(TEST_USER, reminder())
        assertThat(notifyService.getUnreadCount(TEST_USER)).isEqualTo(3)
        assertThat(notifyService.markAllRead(TEST_USER)).isEqualTo(3)
        assertThat(notifyService.getUnreadCount(TEST_USER)).isZero()
        Unit
    }

    @Test
    fun testSendJoltNotificationUsesOwnersToken() = runBlocking {
        createDummyUser("other-user", id = OTHER_USER)
        userService.updateJoltToken(TEST_USER, "mine")
        userService.updateJoltToken(OTHER_USER, "theirs")
        val notification = notifyService.create(TEST_USER, processed("success"))
        coEvery { joltClient.sendNotification(any(), any(), notification.message, any()) } just Runs
        notifyService.sendJoltNotification(TEST_USER, notification, "title")
        coVerify(exactly = 1) { joltClient.sendNotification("mine", "title", notification.message, any()) }
        coVerify(exactly = 0) { joltClient.sendNotification("theirs", any(), any(), any()) }
    }

    @Test
    fun testSendJoltNotificationWithoutTokenIsSkipped() = runBlocking {
        val notification = notifyService.create(TEST_USER, processed("success"))
        notifyService.sendJoltNotification(TEST_USER, notification, "title")
        coVerify(exactly = 0) { joltClient.sendNotification(any(), any(), any(), any()) }
    }

}
