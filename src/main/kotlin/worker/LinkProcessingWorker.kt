package worker

import common.Link
import entry.LinkService
import kotlinx.coroutines.experimental.CompletableDeferred
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.actor
import link.DefaultLinkProcessor
import link.LinkProcessor
import link.YoutubeLinkProcessor
import resource.HTML
import resource.ResourceManager
import resource.ResourceType
import resource.WebResourceRetriever
import suggest.Suggestion

sealed class LinkProcessingRequest
class PersistLinkProcessingRequest(val link: Link) : LinkProcessingRequest()
class SuggestLinkProcessingRequest(val url: String, val response: CompletableDeferred<Suggestion>) : LinkProcessingRequest()

class LinkProcessorWorker(private val resourceManager: ResourceManager, private val linkService: LinkService) : Worker {

    private val processors = listOf<() -> LinkProcessor>({ YoutubeLinkProcessor(WebResourceRetriever()) })

    override fun worker() = actor<LinkProcessingRequest> {
        for (request in channel) {
            when (request) {
                is PersistLinkProcessingRequest -> processLinkPersist(request.link)
                is SuggestLinkProcessingRequest -> processLinkSuggest(request.url, request.response)
            }
        }
    }

    private fun findProcessor(url: String): Deferred<List<LinkProcessor>> = async {
        val procs = processors.asSequence().map { it() }.filter { it.matches(url) }
                .map { it.apply { init(url) } }.toList()
        if (procs.isNotEmpty()) procs else listOf(DefaultLinkProcessor().apply { init(url) })
    }

    private suspend fun processLinkPersist(link: Link) {
        try {
            findProcessor(link.url).await().forEach {
                it.use {
                    val thumb = async { it.generateThumbnail() }
                    val screen = async { it.generateScreenshot() }
                    it.enrich(link.props)
                    thumb.await()?.let { resourceManager.saveGeneratedResource(link.id, ResourceType.THUMBNAIL, it.extension, it.image) }
                    screen.await()?.let { resourceManager.saveGeneratedResource(link.id, ResourceType.SCREENSHOT, it.extension, it.image) }
                    it.html?.let { resourceManager.saveGeneratedResource(link.id, ResourceType.DOCUMENT, HTML, it.toByteArray()) }
                }
            }
            linkService.update(link)
        } catch (e: Exception) {
            // log and reschedule
        }
    }

    private suspend fun processLinkSuggest(url: String, deferred: CompletableDeferred<Suggestion>) {
        try {
            findProcessor(url).await().forEach {
                it.use {
                    val thumb = async { it.generateThumbnail() }
                    val screen = async { it.generateScreenshot() }
                    val thumbPath = thumb.await()?.let { resourceManager.saveTempFile(url, it.image, ResourceType.THUMBNAIL, it.extension) }
                    val screenPath = screen.await()?.let { resourceManager.saveTempFile(url, it.image, ResourceType.SCREENSHOT, it.extension) }
                    it.html?.let { resourceManager.saveTempFile(url, it.toByteArray(), ResourceType.DOCUMENT, HTML) }
                    deferred.complete(Suggestion(it.resolvedUrl, it.title, thumbPath, screenPath))
                }
            }
        } catch (e: Exception) {
            deferred.completeExceptionally(e)
        }
    }
}
