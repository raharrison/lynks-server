package service

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.cio.websocket.Frame
import io.ktor.util.decodeString
import io.mockk.*
import kotlinx.coroutines.experimental.channels.SendChannel
import kotlinx.coroutines.experimental.runBlocking
import notify.Notification
import notify.NotifyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import util.JsonMapper

class NotifyServiceTest {

    private val notifyService = NotifyService()

    @Test
    fun testSendToOpen() = runBlocking {
        val channel = mockk<SendChannel<Frame>>(relaxUnitFun = true)
        every { channel.isClosedForSend } returns false
        coEvery { channel.send(any()) } just Runs

        val channel2 = mockk<SendChannel<Frame>>(relaxUnitFun = true)
        every { channel2.isClosedForSend } returns false
        coEvery { channel2.send(any()) } just Runs

        notifyService.join(channel)
        notifyService.join(channel2)

        notifyService.accept(Notification.reminder(), "body")

        coVerify(exactly = 1) { channel.send(any()) }
        coVerify(exactly = 1) { channel2.send(any()) }
    }

    @Test
    fun testLeave() = runBlocking {
        val channel = mockk<SendChannel<Frame>>(relaxUnitFun = true)
        every { channel.isClosedForSend } returns false

        notifyService.join(channel)
        notifyService.leave(channel)
        notifyService.accept(Notification.reminder(), "body")

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

        notifyService.join(channel)
        notifyService.join(channel2)
        notifyService.accept(Notification.reminder(), "body")

        coVerify(exactly = 1) { channel.send(any()) }
        coVerify(exactly = 0) { channel2.send(any()) }
    }

    @Test
    fun testNotificationContents() = runBlocking {
        val channel = mockk<SendChannel<Frame>>()
        every { channel.isClosedForSend } returns false
        val notification = slot<Frame>()
        coEvery{ channel.send(capture(notification)) } just Runs

        notifyService.join(channel)
        notifyService.accept(Notification.processed("finished"), "body")

        val got = notification.captured.buffer.decodeString()
        val retrieved = JsonMapper.defaultMapper.readValue<Map<String, Any>>(got)

        assertThat(retrieved["entity"]).isEqualTo("String")
        assertThat(retrieved["type"]).isEqualTo("EXECUTED")
        assertThat(retrieved["body"]).isEqualTo("body")
        assertThat(retrieved["message"]).isEqualTo("finished")
        coVerify(exactly = 1) { channel.send(any()) }
    }

}