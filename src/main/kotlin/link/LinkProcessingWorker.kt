package link

import common.Link
import kotlinx.coroutines.experimental.CompletableDeferred
import kotlinx.coroutines.experimental.channels.actor
import resource.HTML
import resource.ResourceManager
import resource.ResourceType
import suggest.Suggestion
import worker.Worker

sealed class LinkProcessingRequest
class PersistLinkProcessingRequest(val link: Link): LinkProcessingRequest()
class SuggestLinkProcessingRequest(val url: String, val response: CompletableDeferred<Suggestion>): LinkProcessingRequest()

class LinkProcessorWorker(private val resourceManager: ResourceManager): Worker {

    private val processors = listOf<() -> LinkProcessor>( { YoutubeLinkProcessor()} )

    override fun worker() = actor<LinkProcessingRequest> {
        for(request in channel) {
            when(request) {
                is PersistLinkProcessingRequest -> processLinkPersist(request.link)
                is SuggestLinkProcessingRequest -> processLinkSuggest(request.url, request.response)
            }
        }
    }

    private fun findProcessor(url: String): LinkProcessor {
        for(proc in processors) {
            val processor = proc()
            if(processor.matches(url))
                return processor.apply { init(url) }
        }
        return DefaultLinkProcessor().apply { init(url) }
    }

    private fun processLinkPersist(link: Link) {
        findProcessor(link.url).use {
            it.generateThumbnail()?.let { resourceManager.saveGeneratedResource(link.id, ResourceType.THUMBNAIL, it.extension, it.image) }
            it.generateScreenshot()?.let { resourceManager.saveGeneratedResource(link.id, ResourceType.SCREENSHOT, it.extension, it.image) }
            it.html?.let { resourceManager.saveGeneratedResource(link.id, ResourceType.DOCUMENT, HTML, it.toByteArray()) }
            it.enrich(link.props)
        }
    }

    private fun processLinkSuggest(url: String, deferred: CompletableDeferred<Suggestion>) {
        try {
            findProcessor(url).use {
                val thumbPath = it.generateThumbnail()?.let { resourceManager.saveTempFile(url, it.image, ResourceType.THUMBNAIL, it.extension) }
                val screenPath = it.generateScreenshot()?.let { resourceManager.saveTempFile(url, it.image, ResourceType.SCREENSHOT, it.extension) }
                it.html?.let { resourceManager.saveTempFile(url, it.toByteArray(), ResourceType.DOCUMENT, HTML) }
                deferred.complete(Suggestion(it.resolvedUrl, it.title, thumbPath, screenPath))

            }
        }catch (e: Exception) {
            deferred.completeExceptionally(e)
        }
    }
}
