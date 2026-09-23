package lynks.entry

import lynks.common.*
import lynks.db.EntryRepository
import lynks.group.GroupSet
import lynks.group.GroupSetService
import lynks.resource.ResourceManager
import lynks.util.markdown.MarkdownProcessor
import lynks.worker.WorkerRegistry
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
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

    override fun typeCondition(table: BaseEntries): Op<Boolean> = table.type eq EntryType.SNIPPET

    override val slimColumnSet: List<Column<*>> =
        listOf(Entries.id, Entries.content, Entries.dateUpdated, Entries.starred)

    override fun add(userId: UserId, entry: NewSnippet): Snippet {
        return super.add(userId, entry).also {
            workerRegistry.acceptEntryRefWork(userId, it.id)
        }
    }

    override fun update(userId: UserId, entry: NewSnippet, newVersion: Boolean): Snippet? {
        return super.update(userId, entry, newVersion).also {
            if (it != null) {
                workerRegistry.acceptEntryRefWork(userId, it.id)
            }
        }
    }

    override fun toInsert(userId: UserId, eId: EntryId, entry: NewSnippet): BaseEntries.(UpdateBuilder<*>) -> Unit {
        val (processedText, html) = markdownProcessor.convertAndProcess(userId, entry.content, eId)
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

    override fun toUpdate(userId: UserId, entry: NewSnippet): BaseEntries.(UpdateBuilder<*>) -> Unit {
        val (processedText, html) = markdownProcessor.convertAndProcess(userId, entry.content, entry.id!!)
        return {
            it[plainContent] = processedText
            it[content] = html
            it[dateUpdated] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    override fun toNewEntry(userId: UserId, entry: Snippet) = NewSnippet(
        entry.id, entry.plainContent, entry.tags.map { it.id }, entry.collections.map { it.id }
    )

    override fun toUpdate(userId: UserId, entry: Snippet): BaseEntries.(UpdateBuilder<*>) -> Unit = {
        it[plainContent] = entry.plainContent
        it[content] = markdownProcessor.convertToMarkdown(userId, entry.plainContent)
        it[props] = entry.props
    }
}
