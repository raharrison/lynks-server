package lynks.notify.pushover

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import lynks.common.exception.ExecutionException
import lynks.resource.WebResourceRetriever
import lynks.util.Result
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PushoverClientTest {

    private val resourceRetriever = mockk<WebResourceRetriever>()
    private val pushoverClient = PushoverClient(resourceRetriever)

    @Test
    fun testSendNotificationSuccess() = runBlocking {
        val result = Result.Success("success")
        coEvery { resourceRetriever.postFormStringResult(any(), any()) } returns result
        pushoverClient.sendNotification("message")
        coVerify(exactly = 1) { resourceRetriever.postFormStringResult(any(), any()) }
    }

    @Test
    fun testSendNotificationFailure() = runBlocking {
        val result = Result.Failure(ExecutionException("failed"))
        coEvery { resourceRetriever.postFormStringResult(any(), any()) } returns result
        assertThrows<ExecutionException> {
            pushoverClient.sendNotification("message")
        }
        coVerify(exactly = 1) { resourceRetriever.postFormStringResult(any(), any()) }
    }

}
