package entry

import common.Entries
import common.EntryType
import common.NewNote
import common.Note
import db.EntryRepository
import org.jetbrains.exposed.sql.ColumnSet
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import tag.TagService
import util.MarkdownUtils
import util.RowMapper

class NoteService(tagService: TagService) : EntryRepository<Note, NewNote>(tagService) {

    override fun toModel(row: ResultRow): Note {
        return RowMapper.toNote(row, ::getTagsForEntry)
    }

    override fun getBaseQuery(base: ColumnSet): Query {
        return Entries.select { Entries.type eq EntryType.NOTE }
    }

    override fun toInsert(eId: String, entry: NewNote): Entries.(UpdateBuilder<*>) -> Unit = {
        it[id] = eId
        it[title] = entry.title
        it[plainContent] = entry.plainText
        it[content] = MarkdownUtils.convertToMarkdown(entry.plainText)
        it[src] = "Me"
        it[type] = EntryType.NOTE
        it[dateUpdated] = System.currentTimeMillis()
    }

    override fun toUpdate(entry: NewNote): Entries.(UpdateBuilder<*>) -> Unit = {
        it[title] = entry.title
        it[plainContent] = entry.plainText
        it[content] = MarkdownUtils.convertToMarkdown(entry.plainText)
        it[dateUpdated] = System.currentTimeMillis()
    }
}