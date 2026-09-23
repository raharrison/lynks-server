package lynks.entry

import lynks.common.*
import lynks.db.EntryRepository
import lynks.group.GroupSet
import lynks.group.GroupSetService
import lynks.resource.ResourceManager
import lynks.resource.ResourceType
import lynks.util.Normalize
import lynks.util.URLUtils
import lynks.util.combine
import lynks.worker.PersistLinkProcessingRequest
import lynks.worker.WorkerRegistry
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.time.ZoneOffset

private val deadLinkJsonbOp: Op<Boolean> = object : Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append(
            "jsonb_exists(${Entries.props.name} -> 'attributes', '$DEAD_LINK_PROP')" +
            " AND NOT (${Entries.props.name} -> 'attributes' @> '{\"$DEAD_LINK_PROP\": false}'::jsonb)"
        )
    }
}

class LinkService(
    groupSetService: GroupSetService, entryAuditService: EntryAuditService,
    resourceManager: ResourceManager, private val workerRegistry: WorkerRegistry
) : EntryRepository<Link, SlimLink, NewLink>(groupSetService, entryAuditService, resourceManager) {

    override fun typeCondition(table: BaseEntries): Op<Boolean> = table.type eq EntryType.LINK

    override val slimColumnSet: List<Column<*>> = listOf(
        Entries.id, Entries.title, Entries.src, Entries.plainContent, Entries.dateUpdated,
        Entries.starred, Entries.thumbnailId, Entries.read
    )

    override fun toInsert(userId: UserId, eId: EntryId, entry: NewLink): BaseEntries.(InsertStatement<*>) -> Unit = {
        val time = OffsetDateTime.now(ZoneOffset.UTC)
        it[id] = eId.value
        it[title] = entry.title
        it[plainContent] = entry.url
        it[src] = URLUtils.extractSource(entry.url)
        it[type] = EntryType.LINK
        it[dateCreated] = time
        it[dateUpdated] = time
        it[read] = false
    }

    override fun toUpdate(userId: UserId, entry: NewLink): BaseEntries.(UpdateBuilder<*>) -> Unit = {
        it[title] = entry.title
        it[plainContent] = entry.url
        it[src] = URLUtils.extractSource(entry.url)
        it[dateUpdated] = OffsetDateTime.now(ZoneOffset.UTC)
    }

    override fun toModel(row: ResultRow, groups: GroupSet, table: BaseEntries): Link {
        return RowMapper.toLink(table, row, groups.tags, groups.collections)
    }

    override fun toSlimModel(row: ResultRow, groups: GroupSet, table: BaseEntries): SlimLink {
        return RowMapper.toSlimLink(table, row, groups.tags, groups.collections)
    }

    override fun add(userId: UserId, entry: NewLink): Link {
        val fullEntry = entry.copy(url = URLUtils.ensureUrlProtocol(entry.url))
        val link = super.add(userId, fullEntry)
        workerRegistry.acceptLinkWork(PersistLinkProcessingRequest(userId, link, ResourceType.linkBaseline(), fullEntry.process))
        if (fullEntry.process)
            workerRegistry.acceptDiscussionWork(userId, link.id)
        return link
    }

    override fun update(userId: UserId, entry: NewLink, newVersion: Boolean): Link? {
        val fullEntry = entry.copy(url = URLUtils.ensureUrlProtocol(entry.url))
        return super.update(userId, fullEntry, newVersion)?.also {
            workerRegistry.acceptLinkWork(
                PersistLinkProcessingRequest(
                    userId,
                    it,
                    ResourceType.linkBaseline(),
                    fullEntry.process
                )
            )
            if (fullEntry.process)
                workerRegistry.acceptDiscussionWork(userId, it.id)
        }
    }

    // Only rescrape when the reverted version points somewhere else
    override fun toNewEntry(userId: UserId, entry: Link) = NewLink(
        entry.id, entry.title, entry.url, entry.tags.map { it.id }, entry.collections.map { it.id },
        process = get(userId, entry.id)?.url != entry.url
    )

    override fun toUpdate(userId: UserId, entry: Link): BaseEntries.(UpdateBuilder<*>) -> Unit = {
        it[Entries.title] = entry.title
        it[Entries.plainContent] = entry.url
        it[Entries.src] = URLUtils.extractSource(entry.url)
        it[Entries.thumbnailId] = entry.thumbnailId?.value
        // explicitly not updating props to prevent overriding
        // content (searchable text) is managed exclusively via updateSearchableContent
    }

    fun read(userId: UserId, id: EntryId, read: Boolean): Link? = transaction {
        val where = baseQuery(userId).combine { Entries.id eq id.value }.where
            ?: throw IllegalStateException("Missing where clause for link read update id=${id.value}")
        Entries.update({ where }) { it[Entries.read] = read }
        val readMessage = if (read) "read" else "unread"
        get(userId, id)?.also {
            entryAuditService.acceptAuditEvent(id, LinkService::class.simpleName, "Link marked as $readMessage")
        }
    }

    fun getUnread(userId: UserId): List<Link> = transaction {
        baseQuery(userId).combine { Entries.read eq false }.map { toModel(userId, it) }
    }

    fun getDead(userId: UserId): List<Link> = transaction {
        baseQuery(userId).combine { Entries.props.isNotNull() and deadLinkJsonbOp }.map { toModel(userId, it) }
    }

    fun getSlim(userId: UserId, ids: List<EntryId>): List<SlimLink> = transaction {
        if (ids.isEmpty()) return@transaction emptyList()
        val values = ids.map { it.value }
        baseQuery(userId).adjustSelect { select(slimColumnSet) }
            .combine { Entries.id inList values }
            .map { toSlimModel(it) }
            .sortedBy { values.indexOf(it.id.value) }
    }

    fun checkExistingWithUrl(userId: UserId, url: String): List<SlimLink> = transaction {
        val fullUrl = URLUtils.ensureUrlProtocol(url)
        baseQuery(userId).adjustSelect { select(slimColumnSet) }.combine { Entries.plainContent eq fullUrl }
            .map { toSlimModel(it) }
    }

    fun updateSearchableContent(userId: UserId, id: EntryId, content: String?): String? = transaction {
        val normalizedContent = content?.let { Normalize.mostCommonWords(Normalize.normalize(it), 500) }
        val where = baseQuery(userId).combine { Entries.id eq id.value }.where
            ?: throw IllegalStateException("Missing where clause for content update id=${id.value}")
        val updated = Entries.update({ where }) { it[Entries.content] = normalizedContent }
        if(updated > 0) normalizedContent else null
    }

}
