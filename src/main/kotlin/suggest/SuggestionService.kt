package suggest

import kotlinx.coroutines.experimental.CompletableDeferred
import worker.SuggestLinkProcessingRequest
import worker.WorkerRegistry

class SuggestionService(private val workerRegistry: WorkerRegistry) {

    suspend fun processLink(url: String): Suggestion {
        val suggestion = CompletableDeferred<Suggestion>()
        workerRegistry.acceptLinkWork(SuggestLinkProcessingRequest(url, suggestion))
        return suggestion.await()
    }
}