package link

import kotlinx.coroutines.experimental.CompletableDeferred
import kotlinx.coroutines.experimental.channels.actor
import resource.ResourceManager
import resource.ResourceType
import suggest.Suggestion
import worker.Worker

sealed class LinkProcessingRequest(val url: String)
class PersistLinkProcessingRequest(url: String, val entryId: String) : LinkProcessingRequest(url)
class SuggestLinkProcessingRequest(url: String, val response: CompletableDeferred<Suggestion>): LinkProcessingRequest(url)

class LinkProcessorWorker(private val resourceManager: ResourceManager): Worker {

    override fun worker() = actor<LinkProcessingRequest> {
        for(request in channel) {
            when(request) {
                is PersistLinkProcessingRequest -> processLinkPersist(request.url, request.entryId)
                is SuggestLinkProcessingRequest -> request.response.complete(processLinkSuggest(request.url))
            }
        }
    }

    private fun processLinkPersist(url: String, entryId: String) {
        DefaultLinkProcessor(url).use {
            resourceManager.saveGeneratedResource(entryId, ResourceType.THUMBNAIL, it.generateThumbnail())
            resourceManager.saveGeneratedResource(entryId, ResourceType.SCREENSHOT, it.generateScreenshot())
            resourceManager.saveGeneratedResource(url, ResourceType.DOCUMENT, it.html.toByteArray())
        }
    }

    private fun processLinkSuggest(url: String): Suggestion {
        DefaultLinkProcessor(url).use {
            val thumb = it.generateThumbnail()
            val screen = it.generateScreenshot()
            val title = it.title
            val cleanUrl = it.resolvedUrl
            val thumbPath = resourceManager.saveTempFile(url, thumb, ResourceType.THUMBNAIL)
            val screenPath = resourceManager.saveTempFile(url, screen, ResourceType.SCREENSHOT)
            resourceManager.saveTempFile(url, it.html.toByteArray(), ResourceType.DOCUMENT)
            return Suggestion(cleanUrl, title, thumbPath, screenPath)
        }
    }
}