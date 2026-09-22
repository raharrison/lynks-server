package lynks.entry

import lynks.common.*
import lynks.db.EntryRepository
import lynks.group.GroupSet
import lynks.group.GroupSetService
import lynks.resource.ResourceManager
import lynks.util.markdown.MarkdownProcessor
import lynks.worker.WorkerRegistry
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnSet
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SnippetService(
    groupSetService: GroupSetService,
    entryAuditService: EntryAuditService,
    resourceManager: ResourceManager,
    private val workerRegistry: WorkerRegistry,
    private val markdownProcessor: MarkdownProcessor
) : EntryRepository<Snippet, SlimSnippet, NewSnippet>(groupSetService, entryAuditService, resourceManager) {

    override fun toModel(row: ResultRow, groups: GroupSet, table: BaseEntries): Snippet {
        return RowMapper.toSnippet(table, row, groups.tags, groups.collections)
    }

    override fun toSlimModel(row: ResultRow, groups: GroupSet, table: BaseEntries): SlimSnippet {
        return RowMapper.toSlimSnippet(table, row, groups.tags, groups.collections)
    }

    override fun getBaseQuery(base: ColumnSet, where: BaseEntries): Query {
        return base.selectAll().where { where.type eq EntryType.SNIPPET }
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

    override fun toInsert(eId: EntryId, entry: NewSnippet): BaseEntries.(UpdateBuilder<*>) -> Unit {
        val (processedText, html) = markdownProcessor.convertAndProcess(entry.content, eId)
        val time = OffsetDateTime.now(ZoneOffset.UTC)
        return {
            it[id] = eId.value
            it[plainContent] = processedText
            it[content] = html
            it[type] = EntryType.SNIPPET
            it[dateCreated] = time
            it[dateUpdated] = time
        }
    }

    override fun toUpdate(entry: NewSnippet): BaseEntries.(UpdateBuilder<*>) -> Unit {
        val (processedText, html) = markdownProcessor.convertAndProcess(entry.content, entry.id!!)
        return {
            it[plainContent] = processedText
            it[content] = html
            it[dateUpdated] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    override fun toUpdate(entry: Snippet): BaseEntries.(UpdateBuilder<*>) -> Unit = {
        it[plainContent] = entry.plainContent
        it[content] = markdownProcessor.convertToMarkdown(entry.plainContent)
        it[props] = entry.props
    }
}
