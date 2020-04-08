package task

import common.inject.Inject
import entry.EntryAuditService
import entry.LinkService
import link.summary.LinkSummarizer
import resource.ResourceRetriever
import util.Result
import util.loggerFor

class LinkSummarizerTask(id: String, entryId: String) : Task<TaskContext>(id, entryId) {

    private val log = loggerFor<LinkSummarizerTask>()

    @Inject
    lateinit var resourceRetriever: ResourceRetriever

    @Inject
    lateinit var linkService: LinkService

    @Inject
    lateinit var entryAuditService: EntryAuditService

    override suspend fun process(context: TaskContext) {
        linkService.get(entryId)?.also {
            val summarizer = LinkSummarizer(resourceRetriever)
            when (val result = summarizer.generateSummary(it.url)) {
                is Result.Success -> {
                    log.info("Summary successfully generated for entryId={} url={} size={}", entryId, it.url, result.value.content.length)
                    val props = it.props
                    props.addAttribute("summary", result.value)
                    linkService.mergeProps(entryId, it.props)
                    entryAuditService.acceptAuditEvent(
                        entryId,
                        LinkSummarizerTask::class.simpleName,
                        "Summary successfully generated with ${result.value.reduced} reduction"
                    )
                }
                is Result.Failure -> {
                    log.error("Link summarizer task failed: ${result.reason.message}")
                    entryAuditService.acceptAuditEvent(
                        entryId,
                        LinkSummarizerTask::class.simpleName,
                        "Error occurred whilst generating summary: ${result.reason.message}"
                    )
                }
            }
        }
    }

    override fun createContext(input: Map<String, String>) = TaskContext(input)

    companion object {
        fun build(): TaskBuilder {
            return TaskBuilder(LinkSummarizerTask::class, TaskContext())
        }
    }

}

