package service

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.experimental.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import suggest.Suggestion
import suggest.SuggestionService
import worker.SuggestLinkProcessingRequest
import worker.WorkerRegistry

class SuggestionServiceTest {

    @Test
    fun testSuggestion() {
        val workerRegistry = mockk<WorkerRegistry>()
        val service = SuggestionService(workerRegistry)

        val suggestion = Suggestion("google.com", "Google", null, null)

        every { workerRegistry.acceptLinkWork(any()) } answers {
            val req = this.firstArg<SuggestLinkProcessingRequest>()
            req.response.complete(suggestion)
        }

        runBlocking {
            val response = service.processLink("google.com")
            assertThat(response).isEqualTo(suggestion)
            assertThat(response.screenshot).isNull()
            assertThat(response.thumbnail).isNull()
            assertThat(response.url).isEqualTo("google.com")
            assertThat(response.title).isEqualTo("Google")
        }
    }

}