package lynks.entry

import lynks.common.*
import lynks.db.EntryRepository
import lynks.group.GroupSet
import lynks.group.GroupSetService
import lynks.resource.ResourceManager
import lynks.util.markdown.MarkdownProcessor
import lynks.worker.WorkerRegistry
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder

class SnippetService(
    groupSetService: GroupSetService,
    entryAuditService: EntryAuditService,
    resourceManager: ResourceManager,
    private val workerRegistry: WorkerRegistry,
    private val markdownProcessor: MarkdownProcessor
) : EntryRepository<Snippet, SlimSnippet, NewSnippet>(groupSetService, entryAuditService, resourceManager) {

    override fun postprocess(eid: String, entry: NewSnippet): Snippet {
        val (replaced, markdown) = markdownProcessor.convertAndProcess(entry.plainText, eid)
        if (replaced > 0) {
            return update(entry.copy(id = eid, plainText = markdown), newVersion = false)!!
        }
        return super.postprocess(eid, entry)
    }

    override fun toModel(row: ResultRow, groups: GroupSet, table: BaseEntries): Snippet {
        return RowMapper.toSnippet(table, row, groups.tags, groups.collections)
    }

    override fun toSlimModel(row: ResultRow, groups: GroupSet, table: BaseEntries): SlimSnippet {
        return RowMapper.toSlimSnippet(table, row, groups.tags, groups.collections)
    }

    override fun getBaseQuery(base: ColumnSet, where: BaseEntries): Query {
        return base.select { where.type eq EntryType.SNIPPET }
    }

    override val slimColumnSet: List<Column<*>> =
        listOf(Entries.id, Entries.content, Entries.dateUpdated, Entries.starred)

    override fun add(entry: NewSnippet): Snippet {
        return super.add(entry).also {
            workerRegistry.acceptEntryRefWork(it.id)
        }
    }

    override fun update(entry: NewSnippet, newVersion: Boolean): Snippet? {
        return super.update(entry, newVersion).also {
            if (it != null) {
                workerRegistry.acceptEntryRefWork(it.id)
            }
        }
    }

    override fun toInsert(eId: String, entry: NewSnippet): BaseEntries.(UpdateBuilder<*>) -> Unit = {
        val time = System.currentTimeMillis()
        it[id] = eId
        it[title] = "Snippet"
        it[plainContent] = entry.plainText
        it[content] = markdownProcessor.convertToMarkdown(entry.plainText)
        it[src] = "me"
        it[type] = EntryType.SNIPPET
        it[dateCreated] = time
        it[dateUpdated] = time
    }

    override fun toUpdate(entry: NewSnippet): BaseEntries.(UpdateBuilder<*>) -> Unit = {
        it[plainContent] = entry.plainText
        it[content] = markdownProcessor.convertToMarkdown(entry.plainText)
        it[dateUpdated] = System.currentTimeMillis()
    }

    override fun toUpdate(entry: Snippet): BaseEntries.(UpdateBuilder<*>) -> Unit = {
        it[plainContent] = entry.plainText
        it[content] = markdownProcessor.convertToMarkdown(entry.plainText)
        it[props] = entry.props
    }
}
