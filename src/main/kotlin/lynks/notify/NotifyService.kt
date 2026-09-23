package lynks.notify

import lynks.common.Entries
import lynks.common.NotificationId
import lynks.common.RowMapper.toNotification
import lynks.common.UserId
import lynks.common.newNotificationId
import lynks.common.page.DefaultPageRequest
import lynks.common.page.Page
import lynks.common.page.PageRequest
import lynks.common.page.SortDirection
import lynks.notify.jolt.JoltClient
import lynks.user.UserService
import lynks.util.findColumn
import lynks.util.loggerFor
import lynks.util.orderBy
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.max

class NotifyService(private val joltClient: JoltClient, private val userService: UserService) {

    private val log = loggerFor<NotifyService>()
    private val notificationQuerySlice = Notifications.columns + listOf(Entries.type, Entries.title)

    fun getNotifications(userId: UserId, pageRequest: PageRequest = DefaultPageRequest): Page<Notification> = transaction {
        val sortColumn = Notifications.findColumn(pageRequest.sort) ?: Notifications.dateCreated
        val sortOrder = pageRequest.direction ?: SortDirection.DESC
        val orders = buildList {
            add(sortColumn to sortOrder)
            // also add secondary sort by date created when using read
            if (sortColumn == Notifications.read) {
                add(Notifications.dateCreated to SortDirection.DESC)
            }
        }
        val baseQuery = Notifications.leftJoin(Entries).select(notificationQuerySlice)
            .where { Notifications.userId eq userId.value }
        Page.of(
            baseQuery.copy()
                .orderBy(orders)
                .limit(pageRequest.size)
                .offset(max(0, (pageRequest.page - 1) * pageRequest.size))
                .map { toNotification(it) }, pageRequest, baseQuery.count()
        )
    }

    fun getNotification(userId: UserId, id: NotificationId): Notification? = transaction {
        Notifications.leftJoin(Entries)
            .select(notificationQuerySlice)
            .where { (Notifications.notificationId eq id.value) and (Notifications.userId eq userId.value) }
            .mapNotNull { toNotification(it) }.singleOrNull()
    }

    fun getUnreadCount(userId: UserId): Long = transaction {
        Notifications.selectAll().where { (Notifications.userId eq userId.value) and (Notifications.read eq false) }.count()
    }

    fun create(userId: UserId, newNotification: NewNotification): Notification = transaction {
        val id = newNotificationId()
        val time = OffsetDateTime.now(ZoneOffset.UTC)
        Notifications.insert {
            it[notificationId] = id.value
            it[Notifications.userId] = userId.value
            it[notificationType] = newNotification.type
            it[message] = newNotification.message
            it[read] = false
            it[entryId] = newNotification.entryId?.value
            it[dateCreated] = time
        }
        getNotification(userId, id) ?: throw IllegalStateException("Notification ${id.value} not found after insert")
    }

    fun read(userId: UserId, id: NotificationId, isRead: Boolean): Int = transaction {
        Notifications.update({ (Notifications.notificationId eq id.value) and (Notifications.userId eq userId.value) }) {
            it[read] = isRead
        }
    }

    fun markAllRead(userId: UserId): Int = transaction {
        Notifications.update({ (Notifications.userId eq userId.value) and (Notifications.read eq false) }) {
            it[read] = true
        }
    }

    suspend fun sendJoltNotification(userId: UserId, notification: Notification, title: String?) {
        val token = userService.getJoltToken(userId)
        if (token == null) {
            log.warn("No jolt token set, unable to send notification user={}", userId)
            return
        }
        joltClient.sendNotification(token, title, notification.message)
    }

}
