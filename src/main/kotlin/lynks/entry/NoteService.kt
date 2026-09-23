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

class NoteService(
    groupSetService: GroupSetService,
    entryAuditService: EntryAuditService,
    resourceManager: ResourceManager,
    private val workerRegistry: WorkerRegistry,
    private val markdownProcessor: MarkdownProcessor
) : EntryRepository<Note, SlimNote, NewNote>(groupSetService, entryAuditService, resourceManager) {

    override fun toModel(row: ResultRow, groups: GroupSet, table: BaseEntries): Note {
        return RowMapper.toNote(table, row, groups.tags, groups.collections)
    }

    override fun toSlimModel(row: ResultRow, groups: GroupSet, table: BaseEntries): SlimNote {
        return RowMapper.toSlimNote(table, row, groups.tags, groups.collections)
    }

    override fun typeCondition(table: BaseEntries): Op<Boolean> = table.type eq EntryType.NOTE

    override val slimColumnSet: List<Column<*>> =
        listOf(Entries.id, Entries.title, Entries.dateUpdated, Entries.starred)

    override fun add(userId: UserId, entry: NewNote): Note {
        return super.add(userId, entry).also {
            workerRegistry.acceptEntryRefWork(userId, it.id)
        }
    }

    override fun update(userId: UserId, entry: NewNote, newVersion: Boolean): Note? {
        return super.update(userId, entry, newVersion).also {
            if (it != null) {
                workerRegistry.acceptEntryRefWork(userId, it.id)
            }
        }
    }

    override fun toInsert(userId: UserId, eId: EntryId, entry: NewNote): BaseEntries.(UpdateBuilder<*>) -> Unit {
        val (processedText, html) = markdownProcessor.convertAndProcess(userId, entry.content, eId)
        val time = OffsetDateTime.now(ZoneOffset.UTC)
        return {
            it[id] = eId.value
            it[title] = entry.title
            it[plainContent] = processedText
            it[content] = html
            it[type] = EntryType.NOTE
            it[dateCreated] = time
            it[dateUpdated] = time
        }
    }

    override fun toUpdate(userId: UserId, entry: NewNote): BaseEntries.(UpdateBuilder<*>) -> Unit {
        val (processedText, html) = markdownProcessor.convertAndProcess(userId, entry.content, entry.id!!)
        return {
            it[title] = entry.title
            it[plainContent] = processedText
            it[content] = html
            it[dateUpdated] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    override fun toNewEntry(userId: UserId, entry: Note) = NewNote(
        entry.id, entry.title, entry.plainContent, entry.tags.map { it.id }, entry.collections.map { it.id }
    )

    override fun toUpdate(userId: UserId, entry: Note): BaseEntries.(UpdateBuilder<*>) -> Unit = {
        it[title] = entry.title
        it[plainContent] = entry.plainContent
        it[content] = markdownProcessor.convertToMarkdown(userId, entry.plainContent)
        it[props] = entry.props
    }
}
