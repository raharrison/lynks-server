package entry

import common.Entries
import common.EntryType
import common.Link
import common.NewLink
import db.EntryRepository
import org.jetbrains.exposed.sql.ColumnSet
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import resource.ResourceManager
import tag.TagService
import util.RowMapper
import util.URLUtils

class LinkService(tagService: TagService, private val resourceManager: ResourceManager) : EntryRepository<Link, NewLink>(tagService) {

    override fun getBaseQuery(base: ColumnSet): Query {
        return Entries.select { Entries.type eq EntryType.LINK }
    }

    override fun toInsert(eId: String, entry: NewLink): Entries.(InsertStatement<*>) -> Unit = {
        it[Entries.id] = eId
        it[Entries.title] = entry.title
        it[Entries.plainContent] = entry.url
        it[Entries.src] = URLUtils.extractSource(entry.url)
        it[Entries.type] = EntryType.LINK
        it[Entries.dateUpdated] = System.currentTimeMillis()
    }

    override fun toUpdate(entry: NewLink): Entries.(UpdateBuilder<*>) -> Unit = {
        it[title] = entry.title
        it[plainContent] = entry.url
        it[src] = URLUtils.extractSource(entry.url)
        it[dateUpdated] = System.currentTimeMillis()
    }

    override fun toModel(row: ResultRow): Link {
        return RowMapper.toLink(row, ::getTagsForEntry)
    }

    override fun add(entry: NewLink): Link {
        val link = super.add(entry)
        resourceManager.moveTempFiles(link.id, link.url)
        return link
    }
}
