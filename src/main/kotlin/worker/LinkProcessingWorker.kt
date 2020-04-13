package worker

import common.Link
import entry.EntryAuditService
import entry.LinkService
import group.GroupSetService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import link.DefaultLinkProcessor
import link.LinkProcessor
import link.YoutubeLinkProcessor
import link.extract.ExtractUtils
import notify.Notification
import notify.NotifyService
import resource.*
import resource.ResourceType.*
import suggest.Suggestion
import java.time.LocalDate
import java.util.*

sealed class LinkProcessingRequest
class PersistLinkProcessingRequest(val link: Link, val resourceSet: EnumSet<ResourceType>, val process: Boolean) :
    LinkProcessingRequest()

class ActiveLinkCheckingRequest(val url: String, val response: CompletableDeferred<Boolean>) : LinkProcessingRequest()
class SuggestLinkProcessingRequest(val url: String, val response: CompletableDeferred<Suggestion>) :
    LinkProcessingRequest()

class LinkProcessorFactory {
    private val processors = listOf<(String) -> LinkProcessor> { YoutubeLinkProcessor(it, WebResourceRetriever()) }

    fun createProcessors(url: String): List<LinkProcessor> {
        val processors = processors.asSequence().map { it(url) }.filter { it.matches() }.toList()
        return if (processors.isNotEmpty()) processors else listOf(DefaultLinkProcessor(url))
    }
}

class LinkProcessorWorker(
    private val resourceManager: ResourceManager,
    private val linkService: LinkService,
    private val groupSetService: GroupSetService,
    notifyService: NotifyService,
    entryAuditService: EntryAuditService
) : ChannelBasedWorker<LinkProcessingRequest>(notifyService, entryAuditService) {

    internal var processorFactory = LinkProcessorFactory()

    override suspend fun doWork(input: LinkProcessingRequest) {
        when (input) {
            is PersistLinkProcessingRequest -> processLinkPersist(input.link, input.resourceSet, input.process)
            is SuggestLinkProcessingRequest -> processLinkSuggest(input.url, input.response)
            is ActiveLinkCheckingRequest -> processActiveCheck(input.url, input.response)
        }
    }

    private suspend fun processLinkPersist(link: Link, resourceSet: EnumSet<ResourceType>, process: Boolean) {
        try {
            val movedResources = resourceManager.moveTempFiles(link.id, link.url)
            findExistingReadableContent(movedResources)?.also {
                link.content = ExtractUtils.extractTextFromHtmlDoc(it)
            }
            val shouldProcess = process && movedResources.isEmpty()

            processorFactory.createProcessors(link.url).forEach {
                it.use { proc ->
                    coroutineScope {
                        proc.enrich(link.props)
                        if (shouldProcess) {
                            runPersistProcessor(link, resourceSet, proc)
                        }
                    }
                }
            }
            link.props.addAttribute("dead", false)
            linkService.mergeProps(link.id, link.props)

            val updatedLink = linkService.update(link)
            log.info("Link processing worker request complete, sending notification entry={}", link.id)
            if (process) {
                entryAuditService.acceptAuditEvent(
                    link.id,
                    LinkProcessorWorker::class.simpleName,
                    "Link processing completed successfully"
                )
                sendNotification(body = updatedLink)
            }
        } catch (e: Exception) {
            log.error("Link processing worker failed for entry={}", link.id, e)
            // mark as dead if processing failed
            link.props.addAttribute("dead", System.currentTimeMillis())
            linkService.mergeProps(link.id, link.props)
            log.info("Link processing worker marked link as dead after failure, sending notification entry={}", link.id)
            entryAuditService.acceptAuditEvent(link.id, LinkProcessorWorker::class.simpleName, "Link processing failed")
            sendNotification(Notification.error("Error occurred processing link"))
            // log and reschedule
        }
    }

    private suspend fun runPersistProcessor(link: Link, resourceSet: EnumSet<ResourceType>, proc: LinkProcessor) {
        if (resourceSet.isEmpty())
            return

        proc.init()
        val thumb = if (resourceSet.contains(THUMBNAIL)) async { proc.generateThumbnail() } else null
        val screen = if (resourceSet.contains(SCREENSHOT)) async { proc.generateScreenshot() } else null

        if (resourceSet.contains(DOCUMENT)) {
            val linkContent = proc.extractLinkContent()
            linkContent.content?.let {
                link.content = ExtractUtils.extractTextFromHtmlDoc(it)
                saveResource(link.id, READABLE, HTML, it.toByteArray())
            }
            proc.html?.let {
                saveResource(link.id, DOCUMENT, HTML, it.toByteArray())
            }
        }
        if (resourceSet.contains(THUMBNAIL)) {
            thumb?.await()?.let {
                saveResource(link.id, THUMBNAIL, it.extension, it.image)
            }
        }
        if (resourceSet.contains(SCREENSHOT)) {
            screen?.await()?.let {
                saveResource(link.id, SCREENSHOT, it.extension, it.image)
            }
        }
    }

    private fun saveResource(id: String, type: ResourceType, extension: String, data: ByteArray): Resource {
        val date = LocalDate.now().toString()
        val resourceFileName =  "${type.name.toLowerCase()}-$date.${extension}"
        return resourceManager.saveGeneratedResource(id, resourceFileName, type, data)
    }

    private fun findExistingReadableContent(resources: List<Resource>): String? {
        return resources.find { it.type == READABLE }?.let {
            return resourceManager.getResourceAsFile(it.id)?.second?.readText()
        }
    }

    private suspend fun processLinkSuggest(url: String, deferred: CompletableDeferred<Suggestion>) {
        try {
            log.info("Link processing worker executing suggestion request for url={}", url)
            processorFactory.createProcessors(url).forEach { it ->
                it.use { proc ->
                    coroutineScope {
                        proc.init()
                        val thumb = async { proc.generateThumbnail() }
                        val screen = async { proc.generateScreenshot() }
                        val thumbPath = thumb.await()
                            ?.let { resourceManager.saveTempFile(url, it.image, THUMBNAIL, it.extension) }
                        val screenPath = screen.await()
                            ?.let { resourceManager.saveTempFile(url, it.image, SCREENSHOT, it.extension) }
                        proc.html?.let {
                            resourceManager.saveTempFile(
                                url,
                                it.toByteArray(),
                                DOCUMENT,
                                HTML
                            )
                        }
                        val linkContent = proc.extractLinkContent()
                        val matchedGroups = groupSetService.matchWithContent(linkContent.content)
                        log.info("Link processing worker completing suggestion request for url={}", url)
                        deferred.complete(Suggestion(proc.resolvedUrl, linkContent.title, thumbPath, screenPath, linkContent.keywords,
                            matchedGroups.tags, matchedGroups.collections))
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Link processing worker failed generating suggestion for url={}", url, e)
            deferred.completeExceptionally(e)
        }
    }

    private suspend fun processActiveCheck(url: String, deferred: CompletableDeferred<Boolean>) {
        try {
            log.info("Link processing worker executing active check request for url={}", url)
            processorFactory.createProcessors(url).forEach {
                it.use { proc ->
                    coroutineScope {
                        proc.init()
                        deferred.complete(true)
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Link processing worker detected dead link for url={}", url, e)
            deferred.complete(false)
        }
    }
}
