package worker

import common.Link
import entry.LinkService
import kotlinx.coroutines.experimental.CompletableDeferred
import kotlinx.coroutines.experimental.async
import link.DefaultLinkProcessor
import link.LinkProcessor
import link.YoutubeLinkProcessor
import notify.Notification
import notify.NotifyService
import resource.HTML
import resource.ResourceManager
import resource.ResourceType
import resource.WebResourceRetriever
import suggest.Suggestion

sealed class LinkProcessingRequest
class PersistLinkProcessingRequest(val link: Link) : LinkProcessingRequest()
class SuggestLinkProcessingRequest(val url: String, val response: CompletableDeferred<Suggestion>) : LinkProcessingRequest()

class LinkProcessorFactory {
    private val processors = listOf<() -> LinkProcessor>({ YoutubeLinkProcessor(WebResourceRetriever()) })

    suspend fun createProcessors(url: String): List<LinkProcessor> {
        val procs = processors.asSequence().map { it() }.filter { it.matches(url) }.toList()
        procs.forEach { it.init(url) }
        return if (procs.isNotEmpty()) procs else listOf(DefaultLinkProcessor().apply { init(url) })
    }
}

class LinkProcessorWorker(private val resourceManager: ResourceManager,
                          private val linkService: LinkService,
                          notifyService: NotifyService) : ChannelBasedWorker<LinkProcessingRequest>(notifyService) {

    internal var processorFactory = LinkProcessorFactory()

    override suspend fun doWork(input: LinkProcessingRequest) {
        when (input) {
            is PersistLinkProcessingRequest -> processLinkPersist(input.link)
            is SuggestLinkProcessingRequest -> processLinkSuggest(input.url, input.response)
        }
    }

    private suspend fun processLinkPersist(link: Link) {
        try {
            processorFactory.createProcessors(link.url).forEach { it ->
                it.use { _ ->
                    val thumb = async(runner) { it.generateThumbnail() }
                    val screen = async(runner) { it.generateScreenshot() }
                    it.enrich(link.props)
                    link.content = it.content
                    thumb.await()?.let { resourceManager.saveGeneratedResource(link.id, ResourceType.THUMBNAIL, it.extension, it.image) }
                    screen.await()?.let { resourceManager.saveGeneratedResource(link.id, ResourceType.SCREENSHOT, it.extension, it.image) }
                    it.html?.let { resourceManager.saveGeneratedResource(link.id, ResourceType.DOCUMENT, HTML, it.toByteArray()) }
                }
            }
            linkService.update(link)
            sendNotification(body=link)
        } catch (e: Exception) {
            sendNotification(Notification.error("Error occurred processing link"))
            // log and reschedule
        }
    }

    private suspend fun processLinkSuggest(url: String, deferred: CompletableDeferred<Suggestion>) {
        try {
            processorFactory.createProcessors(url).forEach { it ->
                it.use { _ ->
                    val thumb = async(runner) { it.generateThumbnail() }
                    val screen = async(runner) { it.generateScreenshot() }
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
