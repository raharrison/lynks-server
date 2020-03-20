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
class PersistLinkProcessingRequest(val link: Link, val process: Boolean) : LinkProcessingRequest()
class SuggestLinkProcessingRequest(val url: String, val response: CompletableDeferred<Suggestion>) : LinkProcessingRequest()

class LinkProcessorFactory {
    private val processors = listOf<() -> LinkProcessor> { YoutubeLinkProcessor(WebResourceRetriever()) }

    fun createProcessors(url: String): List<LinkProcessor> {
        val processors = processors.asSequence().map { it() }.filter { it.matches(url) }.toList()
        return if (processors.isNotEmpty()) processors else listOf(DefaultLinkProcessor())
    }
}

class LinkProcessorWorker(private val resourceManager: ResourceManager,
                          private val linkService: LinkService,
                          notifyService: NotifyService) : ChannelBasedWorker<LinkProcessingRequest>(notifyService) {

    internal var processorFactory = LinkProcessorFactory()

    override suspend fun doWork(input: LinkProcessingRequest) {
        when (input) {
            is PersistLinkProcessingRequest -> processLinkPersist(input.link, input.process)
            is SuggestLinkProcessingRequest -> processLinkSuggest(input.url, input.response)
        }
    }

    private suspend fun processLinkPersist(link: Link, process: Boolean) {
        try {
            val alreadyProcessed = resourceManager.moveTempFiles(link.id, link.url)
            processorFactory.createProcessors(link.url).forEach { it ->
                it.use { proc ->
                    coroutineScope {
                        proc.enrich(link.props)
                        if (!alreadyProcessed && process) {
                            proc.init(link.url)
                            val thumb = async { proc.generateThumbnail() }
                            val screen = async { proc.generateScreenshot() }
                            link.content = proc.content
                            thumb.await()?.let { resourceManager.saveGeneratedResource(link.id, "thumbnail.${it.extension}", ResourceType.THUMBNAIL, it.image) }
                            screen.await()?.let { resourceManager.saveGeneratedResource(link.id, "screenshot.${it.extension}", ResourceType.SCREENSHOT, it.image) }
                            proc.html?.let { resourceManager.saveGeneratedResource(link.id, "document.$HTML", ResourceType.DOCUMENT, it.toByteArray()) }
                        }
                    }
                }
            }
            link.props.addAttribute("dead", false)
            linkService.mergeProps(link.id, link.props)

            val updatedLink = linkService.update(link)
            log.info("Link processing worker request complete, sending notification entry={}", link.id)
            sendNotification(body = updatedLink)
        } catch (e: Exception) {
            log.error("Link processing worker failed for entry={}", link.id, e)
            // mark as dead if processing failed
            link.props.addAttribute("dead", System.currentTimeMillis())
            linkService.mergeProps(link.id, link.props)
            log.info("Link processing worker marked link as dead after failure, sending notification entry={}", link.id)
            sendNotification(Notification.error("Error occurred processing link"))
            // log and reschedule
        }
    }

    private suspend fun processLinkSuggest(url: String, deferred: CompletableDeferred<Suggestion>) {
        try {
            log.info("Link processing worker executing suggestion request for url={}", url)
            processorFactory.createProcessors(url).forEach { it ->
                it.use { proc ->
                    coroutineScope {
                        proc.init(url)
                        val thumb = async { proc.generateThumbnail() }
                        val screen = async { proc.generateScreenshot() }
                        val thumbPath = thumb.await()?.let { resourceManager.saveTempFile(url, it.image, ResourceType.THUMBNAIL, it.extension) }
                        val screenPath = screen.await()?.let { resourceManager.saveTempFile(url, it.image, ResourceType.SCREENSHOT, it.extension) }
                        proc.html?.let { resourceManager.saveTempFile(url, it.toByteArray(), ResourceType.DOCUMENT, HTML) }
                        log.info("Link processing worker completing suggestion request for url={}", url)
                        deferred.complete(Suggestion(proc.resolvedUrl, proc.title, thumbPath, screenPath))
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Link processing worker failed generating suggestion for url={}", url, e)
            deferred.completeExceptionally(e)
        }
    }
}
