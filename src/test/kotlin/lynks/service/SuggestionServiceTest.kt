package lynks.service

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import lynks.suggest.Suggestion
import lynks.suggest.SuggestionService
import lynks.util.TEST_USER
import lynks.worker.SuggestLinkProcessingRequest
import lynks.worker.WorkerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SuggestionServiceTest {

    private val workerRegistry = mockk<WorkerRegistry>()
    private val service = SuggestionService(workerRegistry)

    @Test
    fun testSuggestion() {
        val keywords = setOf("search", "other", "important")
        val suggestion = Suggestion("https://google.com", "Google", null, null, keywords)

        every { workerRegistry.acceptLinkWork(any()) } answers {
            val req = this.firstArg<SuggestLinkProcessingRequest>()
            assertThat(req.url).isEqualTo("https://google.com")
            assertThat(req.userId).isEqualTo(TEST_USER)
            req.response.complete(suggestion)
        }

        runBlocking {
            val response = service.processLink(TEST_USER, "https://google.com")
            assertThat(response).isEqualTo(suggestion)
            assertThat(response.preview).isNull()
            assertThat(response.thumbnail).isNull()
            assertThat(response.keywords).isEqualTo(keywords)
            assertThat(response.url).isEqualTo("https://google.com")
            assertThat(response.title).isEqualTo("Google")
        }
    }

    @Test
    fun testSuggestionMissingUrlProtocol() {
        val suggestion = Suggestion("https://gmail.com", "Gmail", null, null)
        every { workerRegistry.acceptLinkWork(any()) } answers {
            val req = this.firstArg<SuggestLinkProcessingRequest>()
            assertThat(req.url).isEqualTo("https://gmail.com")
            assertThat(req.userId).isEqualTo(TEST_USER)
            req.response.complete(suggestion)
        }

        runBlocking {
            val response = service.processLink(TEST_USER, "gmail.com")
            assertThat(response).isEqualTo(suggestion)
            assertThat(response.preview).isNull()
            assertThat(response.thumbnail).isNull()
            assertThat(response.url).isEqualTo("https://gmail.com")
            assertThat(response.title).isEqualTo("Gmail")
        }
    }
}
