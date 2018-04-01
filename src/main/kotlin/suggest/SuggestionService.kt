package suggest

import kotlinx.coroutines.experimental.CompletableDeferred
import link.SuggestLinkProcessingRequest
import worker.WorkerRegistry

class SuggestionService(private val workerRegistry: WorkerRegistry) {

    //TODO: Handle invalid url and navigation issues
    suspend fun processLink(url: String): Suggestion {
        val suggestion = CompletableDeferred<Suggestion>()
        workerRegistry.acceptLinkWork(SuggestLinkProcessingRequest(url, suggestion))
        return suggestion.await()
    }
}