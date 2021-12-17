package lynks.worker

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import lynks.common.DEAD_LINK_PROP
import lynks.common.Link
import lynks.entry.EntryAuditService
import lynks.entry.LinkService
import lynks.group.GroupSetService
import lynks.link.LinkProcessor
import lynks.link.LinkProcessorFactory
import lynks.notify.Notification
import lynks.notify.NotifyService
import lynks.resource.GeneratedResource
import lynks.resource.Resource
import lynks.resource.ResourceManager
import lynks.resource.ResourceType
import lynks.resource.ResourceType.*
import lynks.suggest.Suggestion
import lynks.util.Normalize
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

sealed class LinkProcessingRequest
class PersistLinkProcessingRequest(val link: Link, val resourceSet: EnumSet<ResourceType>, val process: Boolean) :
    LinkProcessingRequest()

class ActiveLinkCheckingRequest(val url: String, val response: CompletableDeferred<Boolean>) : LinkProcessingRequest()
class SuggestLinkProcessingRequest(val url: String, val response: CompletableDeferred<Suggestion>) :
    LinkProcessingRequest()

class LinkProcessorWorker(
    private val resourceManager: ResourceManager,
    private val linkService: LinkService,
    private val groupSetService: GroupSetService,
    notifyService: NotifyService,
    entryAuditService: EntryAuditService
) : ChannelBasedWorker<LinkProcessingRequest>(notifyService, entryAuditService) {

    internal var processorFactory = LinkProcessorFactory(resourceManager = resourceManager)

    override suspend fun doWork(input: LinkProcessingRequest) {
        when (input) {
            is PersistLinkProcessingRequest -> processLinkPersist(input.link, input.resourceSet, input.process)
            is SuggestLinkProcessingRequest -> processLinkSuggest(input.url, input.response)
            is ActiveLinkCheckingRequest -> processActiveCheck(input.url, input.response)
        }
    }

    private suspend fun processLinkPersist(link: Link, resourceSet: EnumSet<ResourceType>, process: Boolean) {
        try {
            resourceManager.deleteTempFiles(link.url)
            link.props.clearTasks()
            val resources = processorFactory.createProcessors(link.url).flatMap {
                it.use { proc ->
                    coroutineScope {
                        proc.enrich(link.props)
                        if (process) {
                            return@coroutineScope runPersistProcessor(link, resourceSet, proc)
                        }
                        return@coroutineScope emptyList<Resource>()
                    }
                }
            }
            link.thumbnailId = findThumbnail(resources)
            link.props.addAttribute(DEAD_LINK_PROP, false)
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
            link.props.addAttribute(DEAD_LINK_PROP, System.currentTimeMillis())
            linkService.mergeProps(link.id, link.props)
            log.info("Link processing worker marked link as dead after failure, sending notification entry={}", link.id)
            entryAuditService.acceptAuditEvent(link.id, LinkProcessorWorker::class.simpleName, "Link processing failed")
            sendNotification(Notification.error("Error occurred processing link"))
            // log and reschedule
        }
    }

    private fun findThumbnail(resources: List<Resource>): String? {
        return resources
            .filter { it.type == THUMBNAIL }
            .map { it.id }
            .firstOrNull()
    }

    private suspend fun runPersistProcessor(link: Link, resourceSet: EnumSet<ResourceType>, proc: LinkProcessor): List<Resource> {
        if (resourceSet.isEmpty())
            return emptyList()

        proc.init()

        val scrapedResources = proc.scrapeResources(resourceSet)
        val resourcesByType = scrapedResources.associateBy { it.resourceType }
        val generatedResources = mutableMapOf<ResourceType, GeneratedResource>()

        resourceSet.forEach {
            if (resourcesByType.containsKey(it) && it != READABLE_TEXT) {
                generatedResources[it] = resourcesByType.getValue(it)
            } else {
                log.info("Generated resource for {} is empty, not adding to results", it)
            }
        }
        val savedResources = resourceManager.migrateGeneratedResources(link.id, generatedResources.values.toList())

        // find readable resource and assign link content for searching
        resourcesByType[READABLE_TEXT]?.let {
            val readableContent = Files.readString(Path.of(it.targetPath))
            link.content = Normalize.removeStopwords(Normalize.normalize(readableContent))
        }

        return savedResources
    }

    private suspend fun processLinkSuggest(url: String, deferred: CompletableDeferred<Suggestion>) {
        try {
            log.info("Link processing worker executing suggestion request for url={}", url)
            processorFactory.createProcessors(url).forEach { it ->
                it.use { proc ->
                    coroutineScope {
                        proc.init()
                        val resourceSet = EnumSet.of(PREVIEW, THUMBNAIL, READABLE_TEXT)
                        val suggestionResponse = proc.suggest(resourceSet)
                        val resourcesByType = suggestionResponse.resources.associateBy { it.resourceType }

                        val extractedContent = resourcesByType[READABLE_TEXT]?.let {
                            val readableContent = Files.readString(Path.of(it.targetPath))
                            Normalize.normalize(readableContent)
                        }
                        val matchedGroups = groupSetService.matchWithContent(extractedContent)
                        log.info("Link processing worker completing suggestion request for url={}", url)
                        deferred.complete(
                            Suggestion(
                                suggestionResponse.details.url,
                                suggestionResponse.details.title,
                                resourcesByType[THUMBNAIL]?.let { resourceManager.constructTempUrlFromPath(it.targetPath) },
                                resourcesByType[PREVIEW]?.let { resourceManager.constructTempUrlFromPath(it.targetPath) },
                                suggestionResponse.details.keywords,
                                matchedGroups.tags,
                                matchedGroups.collections
                            )
                        )
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
