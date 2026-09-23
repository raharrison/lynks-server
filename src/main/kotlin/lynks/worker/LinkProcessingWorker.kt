package lynks.worker

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import lynks.common.*
import lynks.common.exception.SuggestionUnavailableException
import lynks.entry.EntryAuditService
import lynks.entry.LinkService
import lynks.group.GroupSetService
import lynks.link.LinkProcessor
import lynks.link.LinkProcessorFactory
import lynks.notify.NewNotification
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
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

sealed class LinkProcessingRequest
class PersistLinkProcessingRequest(
    val userId: UserId,
    val link: Link,
    val resourceSet: EnumSet<ResourceType>,
    val process: Boolean
) :
    LinkProcessingRequest()

class ActiveLinkCheckingRequest(val url: String, val response: CompletableDeferred<Boolean>) : LinkProcessingRequest()
class SuggestLinkProcessingRequest(val userId: UserId, val url: String, val response: CompletableDeferred<Suggestion>) :
    LinkProcessingRequest()

class LinkProcessorWorker(
    private val resourceManager: ResourceManager,
    private val linkService: LinkService,
    private val groupSetService: GroupSetService,
    private val notifyService: NotifyService,
    private val entryAuditService: EntryAuditService
) : ChannelBasedWorker<LinkProcessingRequest>() {

    internal var processorFactory = LinkProcessorFactory(resourceManager = resourceManager)

    override suspend fun doWork(input: LinkProcessingRequest) {
        when (input) {
            is PersistLinkProcessingRequest -> processLinkPersist(input.userId, input.link, input.resourceSet, input.process)
            is SuggestLinkProcessingRequest -> processLinkSuggest(input.userId, input.url, input.response)
            is ActiveLinkCheckingRequest -> processActiveCheck(input.url, input.response)
        }
    }

    private suspend fun processLinkPersist(userId: UserId, link: Link, resourceSet: EnumSet<ResourceType>, process: Boolean) {
        try {
            resourceManager.deleteTempFiles(link.url)
            val enrichedProps = BaseProperties()
            val resources = processorFactory.createProcessors(link.url).flatMap {
                it.use { proc ->
                    coroutineScope {
                        proc.enrich(enrichedProps)
                        if (process) {
                            return@coroutineScope runPersistProcessor(userId, link, resourceSet, proc)
                        }
                        return@coroutineScope emptyList()
                    }
                }
            }
            val updatedLink = link.copy(thumbnailId = findThumbnail(resources) ?: link.thumbnailId)
            enrichedProps.addAttribute(DEAD_LINK_PROP, false)
            linkService.mergeProps(userId, updatedLink.id, enrichedProps)

            if (updatedLink != link) {
                linkService.update(userId, updatedLink)
            } else {
                log.info("No changes found after link processing, not updating entity")
            }
            log.info("Link processing worker request complete, saved {} resources for entry={}", resources.size, updatedLink.id)
            val message = "Link processed successfully, ${resources.size} resources created"
            if (process) {
                entryAuditService.acceptAuditEvent(
                    updatedLink.id,
                    LinkProcessorWorker::class.simpleName,
                    message
                )
                notifyService.create(userId, NewNotification.processed(message, updatedLink.id))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Link processing worker failed for entry={}", link.id, e)
            // mark as dead if processing failed
            val deadProps = BaseProperties()
            deadProps.addAttribute(DEAD_LINK_PROP, OffsetDateTime.now(ZoneOffset.UTC))
            linkService.mergeProps(userId, link.id, deadProps)
            log.info("Link processing worker marked link as dead after failure, sending notification entry={}", link.id)
            entryAuditService.acceptAuditEvent(link.id, LinkProcessorWorker::class.simpleName, "Link processing failed")
            notifyService.create(userId, NewNotification.error("An error occurred whilst processing the link", link.id))
        }
    }

    private fun findThumbnail(resources: List<Resource>): ResourceId? {
        return resources
            .filter { it.type == THUMBNAIL }
            .map { it.id }
            .firstOrNull()
    }

    private suspend fun runPersistProcessor(
        userId: UserId,
        link: Link,
        resourceSet: EnumSet<ResourceType>,
        proc: LinkProcessor
    ): List<Resource> {
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

        // find readable resource and update link content for searching
        resourcesByType[READABLE_TEXT]?.let {
            val readableContent = Files.readString(Path.of(it.targetPath))
            linkService.updateSearchableContent(userId, link.id, readableContent)
        }

        return savedResources
    }

    private suspend fun processLinkSuggest(userId: UserId, url: String, deferred: CompletableDeferred<Suggestion>) {
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
                        val matchedGroups = groupSetService.matchWithContent(userId, extractedContent)
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: SuggestionUnavailableException) {
            log.warn("Link processing worker suggestion unavailable for url={}: {}", url, e.message, e.cause)
            deferred.completeExceptionally(e)
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Link processing worker detected dead link for url={}", url, e)
            deferred.complete(false)
        }
    }
}
