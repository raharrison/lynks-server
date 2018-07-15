package entry

import common.*
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
import worker.PersistLinkProcessingRequest
import worker.WorkerRegistry

class LinkService(tagService: TagService, private val resourceManager: ResourceManager,
                  private val workerRegistry: WorkerRegistry) : EntryRepository<Link, NewLink>(tagService) {

    override fun getBaseQuery(base: ColumnSet): Query {
        return base.select { Entries.type eq EntryType.LINK }
    }

    override fun toInsert(eId: String, entry: NewLink): BaseEntries.(InsertStatement<*>) -> Unit = {
        it[id] = eId
        it[title] = entry.title
        it[plainContent] = entry.url
        it[src] = URLUtils.extractSource(entry.url)
        it[type] = EntryType.LINK
        it[dateUpdated] = System.currentTimeMillis()
    }

    override fun toUpdate(entry: NewLink): BaseEntries.(UpdateBuilder<*>) -> Unit = {
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
        if(!resourceManager.moveTempFiles(link.id, link.url)) {
            if(entry.process)
                workerRegistry.acceptLinkWork(PersistLinkProcessingRequest(link))
        }
        if(entry.process)
            workerRegistry.acceptDiscussionWork(link)
        return link
    }

    override fun delete(id: String): Boolean {
        return super.delete(id) && resourceManager.deleteAll(id)
    }

    override fun toUpdate(entry: Link): BaseEntries.(UpdateBuilder<*>) -> Unit = {
        it[Entries.title] = entry.title
        it[Entries.plainContent] = entry.url
        it[Entries.content] = entry.content
        it[Entries.src] = URLUtils.extractSource(entry.url)
        it[Entries.dateUpdated] = System.currentTimeMillis()
        it[Entries.props] = entry.props
    }
}
