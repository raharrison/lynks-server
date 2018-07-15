package entry

import common.BaseEntries
import common.EntryType
import common.NewNote
import common.Note
import db.EntryRepository
import org.jetbrains.exposed.sql.ColumnSet
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import resource.ResourceManager
import tag.TagService
import util.MarkdownUtils
import util.RowMapper

class NoteService(tagService: TagService, private val resourceManager: ResourceManager) : EntryRepository<Note, NewNote>(tagService) {

    override fun toModel(row: ResultRow, table: BaseEntries): Note {
        return RowMapper.toNote(table, row, ::getTagsForEntry)
    }

    override fun getBaseQuery(base: ColumnSet, where: BaseEntries): Query {
        return base.select { where.type eq EntryType.NOTE }
    }

    override fun toInsert(eId: String, entry: NewNote): BaseEntries.(UpdateBuilder<*>) -> Unit = {
        it[id] = eId
        it[title] = entry.title
        it[plainContent] = entry.plainText
        it[content] = MarkdownUtils.convertToMarkdown(entry.plainText)
        it[src] = "Me"
        it[type] = EntryType.NOTE
        it[dateUpdated] = System.currentTimeMillis()
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
        it[dateUpdated] = System.currentTimeMillis()
        it[props] = entry.props
    }

    override fun delete(id: String): Boolean {
        return super.delete(id) && resourceManager.deleteAll(id)
    }
}
