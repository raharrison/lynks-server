package lynks.notify

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.channels.SendChannel
import lynks.common.Entries
import lynks.common.Environment
import lynks.common.RowMapper.toNotification
import lynks.common.page.DefaultPageRequest
import lynks.common.page.Page
import lynks.common.page.PageRequest
import lynks.common.page.SortDirection
import lynks.notify.pushover.PushoverClient
import lynks.user.UserService
import lynks.util.JsonMapper.defaultMapper
import lynks.util.RandomUtils
import lynks.util.findColumn
import lynks.util.loggerFor
import lynks.util.orderBy
import org.apache.commons.mail.HtmlEmail
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class NotifyService(private val userService: UserService, private val pushoverClient: PushoverClient) {

    private val log = loggerFor<NotifyService>()
    private val webNotifiers = ConcurrentHashMap.newKeySet<SendChannel<Frame>>()
    private val notificationQuerySlice = Notifications.columns + listOf(Entries.type, Entries.title)

    fun getNotifications(pageRequest: PageRequest = DefaultPageRequest): Page<Notification> = transaction {
        val sortColumn = Notifications.findColumn(pageRequest.sort) ?: Notifications.dateCreated
        val sortOrder = pageRequest.direction ?: SortDirection.DESC
        val baseQuery = Notifications.leftJoin(Entries).slice(notificationQuerySlice).selectAll()
        Page.of(
            baseQuery.copy()
                .orderBy(sortColumn, sortOrder)
                .limit(pageRequest.size, max(0, (pageRequest.page - 1) * pageRequest.size))
                .map { toNotification(it) }, pageRequest, baseQuery.count()
        )
    }

    fun getNotification(id: String): Notification? = transaction {
        Notifications.leftJoin(Entries).slice(notificationQuerySlice)
            .select { Notifications.notificationId eq id }
            .mapNotNull { toNotification(it) }.singleOrNull()
    }

    suspend fun create(newNotification: NewNotification, sendWeb: Boolean = true): Notification {
        val notification = transaction {
            val id = RandomUtils.generateUid()
            val time = System.currentTimeMillis()
            Notifications.insert {
                it[notificationId] = id
                it[notificationType] = newNotification.type
                it[message] = newNotification.message
                it[read] = false
                it[entryId] = newNotification.entryId
                it[dateCreated] = time
            }
            getNotification(id)!!
        }
        if (sendWeb) {
            sendWebNotification(notification)
        }
        return notification
    }

    fun read(id: String, isRead: Boolean): Int = transaction {
        Notifications.update({ Notifications.notificationId eq id }) {
            it[read] = isRead
        }
    }

    fun join(outgoing: SendChannel<Frame>) {
        webNotifiers += outgoing
    }

    fun leave(outgoing: SendChannel<Frame>) {
        webNotifiers -= outgoing
    }

    suspend fun sendWebNotification(notification: Notification) {
        log.info("Sending web ${notification.type} notification: ${notification.message}")
        webNotifiers.forEach {
            if (it.isClosedForSend) {
                log.warn("Notifier is closed for sending, removing from pool")
                webNotifiers.remove(it)
            } else {
                val payload = defaultMapper.writeValueAsString(notification)
                it.send(Frame.Text(payload))
            }
        }
    }

    fun sendEmail(subject: String, body: String) {
        if (!Environment.mail.enabled) return
        val address = userService.currentUserPreferences.email
        address?.let {
            log.info("Sending notification email subject={}", subject)
            val email = HtmlEmail()
            email.hostName = Environment.mail.server
            email.setSmtpPort(Environment.mail.port)
            email.setFrom("noreply@lynks.com")
            email.addTo(it)
            email.subject = subject
            email.setHtmlMsg(body)
            email.send()
        }
    }

    suspend fun sendPushoverNotification(notification: Notification, title: String?) {
        pushoverClient.sendNotification(title, notification.message)
    }

}
