package lynks.entry

import lynks.common.*
import lynks.db.EntryRepository
import lynks.db.like
import lynks.group.GroupSet
import lynks.group.GroupSetService
import lynks.resource.ResourceManager
import lynks.resource.ResourceType
import lynks.util.URLUtils
import lynks.util.combine
import lynks.worker.PersistLinkProcessingRequest
import lynks.worker.WorkerRegistry
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction

class LinkService(
    groupSetService: GroupSetService, entryAuditService: EntryAuditService,
    resourceManager: ResourceManager, private val workerRegistry: WorkerRegistry
) : EntryRepository<Link, SlimLink, NewLink>(groupSetService, entryAuditService, resourceManager) {

    override fun getBaseQuery(base: ColumnSet, where: BaseEntries): Query {
        return base.select { where.type eq EntryType.LINK }
    }

    override val slimColumnSet: List<Column<*>> = listOf(
        Entries.id, Entries.title, Entries.src, Entries.dateUpdated,
        Entries.starred, Entries.thumbnailId, Entries.read
    )

    override fun toInsert(eId: String, entry: NewLink): BaseEntries.(InsertStatement<*>) -> Unit = {
        val time = System.currentTimeMillis()
        it[id] = eId
        it[title] = entry.title
        it[plainContent] = entry.url
        it[src] = URLUtils.extractSource(entry.url)
        it[type] = EntryType.LINK
        it[dateCreated] = time
        it[dateUpdated] = time
        it[read] = false
    }

    override fun toUpdate(entry: NewLink): BaseEntries.(UpdateBuilder<*>) -> Unit = {
        it[title] = entry.title
        it[plainContent] = entry.url
        it[src] = URLUtils.extractSource(entry.url)
        it[dateUpdated] = System.currentTimeMillis()
    }

    override fun toModel(row: ResultRow, groups: GroupSet, table: BaseEntries): Link {
        return RowMapper.toLink(table, row, groups.tags, groups.collections)
    }

    override fun toSlimModel(row: ResultRow, groups: GroupSet, table: BaseEntries): SlimLink {
        return RowMapper.toSlimLink(table, row, groups.tags, groups.collections)
    }

    override fun add(entry: NewLink): Link {
        val link = super.add(entry)
        workerRegistry.acceptLinkWork(PersistLinkProcessingRequest(link, ResourceType.linkBaseline(), entry.process))
        if (entry.process)
            workerRegistry.acceptDiscussionWork(link.id)
        return link
    }

    override fun update(entry: NewLink, newVersion: Boolean): Link? {
        return super.update(entry, newVersion)?.also {
            workerRegistry.acceptLinkWork(PersistLinkProcessingRequest(it, ResourceType.linkBaseline(), entry.process))
            if (entry.process)
                workerRegistry.acceptDiscussionWork(it.id)
        }
    }

    override fun toUpdate(entry: Link): BaseEntries.(UpdateBuilder<*>) -> Unit = {
        it[Entries.title] = entry.title
        it[Entries.plainContent] = entry.url
        it[Entries.content] = entry.content
        it[Entries.src] = URLUtils.extractSource(entry.url)
        it[Entries.thumbnailId] = entry.thumbnailId
        // explicitly not updating props to prevent overriding
    }

    fun read(id: String, read: Boolean): Link? = transaction {
        Entries.update({ getBaseQuery().combine { Entries.id eq id }.where!! }) { it[Entries.read] = read }
        val readMessage = if (read) "read" else "unread"
        get(id)?.also {
            entryAuditService.acceptAuditEvent(id, LinkService::class.simpleName, "Link marked as $readMessage")
        }
    }

    fun getUnread(): List<Link> = transaction {
        getBaseQuery().combine { Entries.read eq false }.map { toModel(it) }
    }

    fun getDead(): List<Link> = transaction {
        getBaseQuery().combine { Entries.props like "%\"$DEAD_LINK_PROP\":true%" }.map { toModel(it) }
    }

    fun checkExistingWithUrl(url: String): List<SlimLink> = transaction {
        getBaseQuery().adjustSlice { slice(slimColumnSet) }.combine { Entries.plainContent eq url }.map { toSlimModel(it) }
    }

}
