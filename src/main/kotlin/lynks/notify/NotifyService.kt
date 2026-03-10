package lynks.notify

import io.ktor.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import lynks.common.Entries
import lynks.common.Environment
import lynks.common.NotificationId
import lynks.common.RowMapper.toNotification
import lynks.common.newNotificationId
import lynks.common.page.DefaultPageRequest
import lynks.common.page.Page
import lynks.common.page.PageRequest
import lynks.common.page.SortDirection
import lynks.notify.pushover.PushoverClient
import lynks.util.JsonMapper.defaultMapper
import lynks.util.findColumn
import lynks.util.loggerFor
import lynks.util.orderBy
import org.apache.commons.mail.HtmlEmail
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class NotifyService(private val pushoverClient: PushoverClient) {

    private val log = loggerFor<NotifyService>()
    private val webNotifiers = ConcurrentHashMap.newKeySet<SendChannel<Frame>>()
    private val notificationQuerySlice = Notifications.columns + listOf(Entries.type, Entries.title)

    fun getNotifications(pageRequest: PageRequest = DefaultPageRequest): Page<Notification> = transaction {
        val sortColumn = Notifications.findColumn(pageRequest.sort) ?: Notifications.dateCreated
        val sortOrder = pageRequest.direction ?: SortDirection.DESC
        val orders = buildList {
            add(sortColumn to sortOrder)
            // also add secondary sort by date created when using read
            if (sortColumn == Notifications.read) {
                add(Notifications.dateCreated to SortDirection.DESC)
            }
        }
        val baseQuery = Notifications.leftJoin(Entries).selectAll()
        Page.of(
            baseQuery.copy()
                .orderBy(orders)
                .limit(pageRequest.size)
                .offset(max(0, (pageRequest.page - 1) * pageRequest.size))
                .map { toNotification(it) }, pageRequest, baseQuery.count()
        )
    }

    fun getNotification(id: NotificationId): Notification? = transaction {
        Notifications.leftJoin(Entries)
            .select(notificationQuerySlice)
            .where { Notifications.notificationId eq id.value }
            .mapNotNull { toNotification(it) }.singleOrNull()
    }

    fun getUnreadCount(): Long = transaction {
        Notifications.selectAll().where { Notifications.read eq false }.count()
    }

    suspend fun create(newNotification: NewNotification, sendWeb: Boolean = true): Notification {
        val notification = transaction {
            val id = newNotificationId()
            val time = System.currentTimeMillis()
            Notifications.insert {
                it[notificationId] = id.value
                it[notificationType] = newNotification.type
                it[message] = newNotification.message
                it[read] = false
                it[entryId] = newNotification.entryId?.value
                it[dateCreated] = time
            }
            getNotification(id) ?: throw IllegalStateException("Notification ${id.value} not found after insert")
        }
        if (sendWeb) {
            sendWebNotification(notification)
        }
        return notification
    }

    fun read(id: NotificationId, isRead: Boolean): Int = transaction {
        Notifications.update({ Notifications.notificationId eq id.value }) {
            it[read] = isRead
        }
    }

    fun markAllRead(): Int = transaction {
        Notifications.update({ Notifications.read eq false }) {
            it[read] = true
        }
    }

    fun join(outgoing: SendChannel<Frame>) {
        webNotifiers += outgoing
    }

    fun leave(outgoing: SendChannel<Frame>) {
        webNotifiers -= outgoing
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun sendWebNotification(notification: Notification) {
        log.info("Sending web ${notification.type} notification: ${notification.message}")
        val payload = defaultMapper.writeValueAsString(notification)
        val toRemove = mutableListOf<SendChannel<Frame>>()
        for (channel in webNotifiers) {
            if (channel.isClosedForSend) {
                toRemove += channel
                continue
            }
            val result = runCatching { channel.send(Frame.Text(payload)) }
            if (result.isFailure) {
                log.warn("Failed to send web notification, removing notifier", result.exceptionOrNull())
                toRemove += channel
            }
        }
        toRemove.forEach {
            webNotifiers.remove(it)
            runCatching { it.close() }
        }
    }

    fun sendEmail(address: String, subject: String, body: String) {
        if (!Environment.mail.enabled) return
        log.info("Sending email with address={} subject={}", address, subject)
        val email = HtmlEmail()
        email.hostName = Environment.mail.server
        email.setSmtpPort(Environment.mail.port)
        email.setFrom("noreply@lynks.com")
        email.addTo(address)
        email.subject = subject
        email.setHtmlMsg(body)
        email.send()
    }

    suspend fun sendPushoverNotification(notification: Notification, title: String?) {
        pushoverClient.sendNotification(title, notification.message)
    }

}
