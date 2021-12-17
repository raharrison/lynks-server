package lynks.suggest

import kotlinx.coroutines.CompletableDeferred
import lynks.util.loggerFor
import lynks.worker.SuggestLinkProcessingRequest
import lynks.worker.WorkerRegistry

class SuggestionService(private val workerRegistry: WorkerRegistry) {

    private val log = loggerFor<SuggestionService>()

    suspend fun processLink(url: String): Suggestion {
        val suggestion = CompletableDeferred<Suggestion>()
        workerRegistry.acceptLinkWork(SuggestLinkProcessingRequest(url, suggestion))
        log.info("Submitted suggestion worker request url={} awaiting result..", url)
        return suggestion.await()
    }
}
