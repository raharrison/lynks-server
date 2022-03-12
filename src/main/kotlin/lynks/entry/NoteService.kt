package lynks.entry

import lynks.common.*
import lynks.db.EntryRepository
import lynks.group.GroupSet
import lynks.group.GroupSetService
import lynks.resource.ResourceManager
import lynks.resource.TempImageMarkdownVisitor
import lynks.util.markdown.MarkdownUtils
import org.jetbrains.exposed.sql.ColumnSet
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.UpdateBuilder

class NoteService(
    groupSetService: GroupSetService, entryAuditService: EntryAuditService, resourceManager: ResourceManager
) : EntryRepository<Note, SlimNote, NewNote>(groupSetService, entryAuditService, resourceManager) {

    override fun postprocess(eid: String, entry: NewNote): Note {
        val (replaced, markdown) = MarkdownUtils.visitAndReplaceNodes(entry.plainText, TempImageMarkdownVisitor(eid, resourceManager))
        if(replaced > 0) {
            return update(entry.copy(id = eid, plainText = markdown), newVersion = false)!!
        }
        return super.postprocess(eid, entry)
    }

    override fun toModel(row: ResultRow, groups: GroupSet, table: BaseEntries): Note {
        return RowMapper.toNote(table, row, groups.tags, groups.collections)
    }

    override fun toSlimModel(row: ResultRow, groups: GroupSet, table: BaseEntries): SlimNote {
        return RowMapper.toSlimNote(table, row, groups.tags, groups.collections)
    }

    override fun getBaseQuery(base: ColumnSet, where: BaseEntries): Query {
        return base.select { where.type eq EntryType.NOTE }
    }

    override fun toInsert(eId: String, entry: NewNote): BaseEntries.(UpdateBuilder<*>) -> Unit = {
        val time = System.currentTimeMillis()
        it[id] = eId
        it[title] = entry.title
        it[plainContent] = entry.plainText
        it[content] = MarkdownUtils.convertToMarkdown(entry.plainText)
        it[src] = "me"
        it[type] = EntryType.NOTE
        it[dateCreated] = time
        it[dateUpdated] = time
    }

    override fun toUpdate(entry: NewNote): BaseEntries.(UpdateBuilder<*>) -> Unit = {
        it[title] = entry.title
        it[plainContent] = entry.plainText
        it[content] = MarkdownUtils.convertToMarkdown(entry.plainText)
        it[dateUpdated] = System.currentTimeMillis()
    }

    override fun toUpdate(entry: Note): BaseEntries.(UpdateBuilder<*>) -> Unit = {
        it[title] = entry.title
        it[plainContent] = entry.plainText
        it[content] = MarkdownUtils.convertToMarkdown(entry.plainText)
        it[props] = entry.props
    }
}
