package lynks.task.link

import lynks.common.inject.Inject
import lynks.entry.EntryAuditService
import lynks.entry.LinkService
import lynks.link.summary.LinkSummarizer
import lynks.resource.ResourceRetriever
import lynks.task.Task
import lynks.task.TaskBuilder
import lynks.task.TaskContext
import lynks.util.Result
import lynks.util.loggerFor

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

    override fun createContext(params: Map<String, String>) = TaskContext(params)

    companion object {
        fun build(): TaskBuilder {
            return TaskBuilder(LinkSummarizerTask::class)
        }
    }

}

