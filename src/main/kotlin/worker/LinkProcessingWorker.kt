package worker

import common.Link
import entry.LinkService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
                it.use { proc ->
                    coroutineScope {
                        val thumb = async { proc.generateThumbnail() }
                        val screen = async { proc.generateScreenshot() }
                        proc.enrich(link.props)
                        link.content = proc.content
                        thumb.await()?.let { resourceManager.saveGeneratedResource(link.id, "thumbnail.${it.extension}", ResourceType.THUMBNAIL, it.image) }
                        screen.await()?.let { resourceManager.saveGeneratedResource(link.id, "screenshot.${it.extension}", ResourceType.SCREENSHOT, it.image) }
                        proc.html?.let { resourceManager.saveGeneratedResource(link.id, "document.$HTML", ResourceType.DOCUMENT, it.toByteArray()) }
                    }
                }
            }
            link.props.addAttribute("dead", false)
            linkService.mergeProps(link.id, link.props)

            val updatedLink = linkService.update(link)
            sendNotification(body=updatedLink)
        } catch (e: Exception) {
            // mark as dead if processing failed
            link.props.addAttribute("dead", System.currentTimeMillis())
            linkService.mergeProps(link.id, link.props)

            sendNotification(Notification.error("Error occurred processing link"))
            // log and reschedule
        }
    }

    private suspend fun processLinkSuggest(url: String, deferred: CompletableDeferred<Suggestion>) {
        try {
            processorFactory.createProcessors(url).forEach { it ->
                it.use { proc ->
                    coroutineScope {
                        val thumb = async { proc.generateThumbnail() }
                        val screen = async { proc.generateScreenshot() }
                        val thumbPath = thumb.await()?.let { resourceManager.saveTempFile(url, it.image, ResourceType.THUMBNAIL, it.extension) }
                        val screenPath = screen.await()?.let { resourceManager.saveTempFile(url, it.image, ResourceType.SCREENSHOT, it.extension) }
                        proc.html?.let { resourceManager.saveTempFile(url, it.toByteArray(), ResourceType.DOCUMENT, HTML) }
                        deferred.complete(Suggestion(proc.resolvedUrl, proc.title, thumbPath, screenPath))
                    }
                }
            }
        } catch (e: Exception) {
            deferred.completeExceptionally(e)
        }
    }
}
