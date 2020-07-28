package worker

import common.Link
import entry.EntryAuditService
import entry.LinkService
import group.GroupSetService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import link.*
import link.extract.ExtractUtils
import link.extract.ExtractionPolicy
import notify.Notification
import notify.NotifyService
import resource.Resource
import resource.ResourceManager
import resource.ResourceType
import resource.ResourceType.*
import resource.WebResourceRetriever
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
    private val retriever = WebResourceRetriever()
    private val processors =
        listOf<(ExtractionPolicy, String) -> LinkProcessor> { policy: ExtractionPolicy, url: String ->
            YoutubeLinkProcessor(policy, url, retriever)
        }

    fun createProcessors(url: String, extractionPolicy: ExtractionPolicy): List<LinkProcessor> {
        val processors = processors.asSequence().map { it(extractionPolicy, url) }.filter { it.matches() }.toList()
        return if (processors.isNotEmpty()) processors else listOf(
            DefaultLinkProcessor(
                extractionPolicy,
                url,
                retriever
            )
        )
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
            resourceManager.deleteTempFiles(link.url)
            val resources = processorFactory.createProcessors(link.url, ExtractionPolicy.FULL).flatMap {
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

        val generatedResources = proc.process(resourceSet)

        val savedResources = generatedResources.map { entry ->
            when (val resource = entry.value) {
                is GeneratedImageResource -> saveResource(link.id, entry.key, resource.image, resource.extension)
                is GeneratedDocResource -> saveResource(
                    link.id,
                    entry.key,
                    resource.doc.toByteArray(),
                    resource.extension
                )
            }
        }

        // find first readable or page resource and assign link content
        (generatedResources[READABLE] ?: generatedResources[PAGE])?.let {
            if (it is GeneratedDocResource) {
                link.content = ExtractUtils.extractTextFromHtmlDoc(it.doc)
            }
        }
        return savedResources
    }

    private fun saveResource(id: String, type: ResourceType, data: ByteArray, extension: String): Resource {
        val date = LocalDate.now().toString()
        val resourceFileName = "${type.name.toLowerCase()}-$date.${extension}"
        return resourceManager.saveGeneratedResource(id, resourceFileName, type, data)
    }

    private suspend fun processLinkSuggest(url: String, deferred: CompletableDeferred<Suggestion>) {
        try {
            log.info("Link processing worker executing suggestion request for url={}", url)
            processorFactory.createProcessors(url, ExtractionPolicy.PARTIAL).forEach { it ->
                it.use { proc ->
                    coroutineScope {
                        proc.init()
                        val resourceSet = EnumSet.of(PREVIEW, THUMBNAIL)
                        val generatedResources = proc.process(resourceSet)
                        val processedResources = generatedResources.mapValues { entry ->
                            when (val resource = entry.value) {
                                is GeneratedImageResource ->
                                    resourceManager.saveTempFile(
                                        url, resource.image, entry.key, resource.extension
                                    )
                                is GeneratedDocResource -> resourceManager.saveTempFile(
                                    url, resource.doc.toByteArray(), entry.key, resource.extension
                                )
                            }
                        }

                        val linkContent = proc.linkContent
                        val matchedGroups = groupSetService.matchWithContent(linkContent.extractedContent)
                        log.info("Link processing worker completing suggestion request for url={}", url)
                        deferred.complete(
                            Suggestion(
                                proc.resolvedUrl,
                                linkContent.title,
                                processedResources[THUMBNAIL],
                                processedResources[PREVIEW],
                                linkContent.keywords,
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
            processorFactory.createProcessors(url, ExtractionPolicy.PARTIAL).forEach {
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
