package lynks.db

import lynks.common.*
import lynks.common.page.DefaultPageRequest
import lynks.common.page.Page
import lynks.common.page.PageRequest
import lynks.common.page.SortDirection
import lynks.entry.EntryAuditService
import lynks.group.EntryGroups
import lynks.group.GroupSet
import lynks.group.GroupSetService
import lynks.resource.ResourceManager
import lynks.util.combine
import lynks.util.findColumn
import lynks.util.orderBy
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.max

abstract class EntryRepository<T : Entry, S : SlimEntry, U : NewEntry>(
    protected val groupSetService: GroupSetService,
    protected val entryAuditService: EntryAuditService,
    protected val resourceManager: ResourceManager
) {

    fun get(id: EntryId): T? = transaction {
        getBaseQuery().combine { Entries.id eq id.value }
            .mapNotNull { toModel(it) }
            .singleOrNull()
    }

    fun get(id: EntryId, version: Int): T? = transaction {
        getBaseQuery(EntryVersions, EntryVersions).combine {
            EntryVersions.id eq id.value and
                (EntryVersions.version eq version)
        }
            .mapNotNull { toModel(it, EntryVersions) }
            .singleOrNull()
    }

    fun get(pageRequest: PageRequest = DefaultPageRequest): Page<S> = transaction {
        val queries = createPagedQuery(pageRequest)
        Page.of(resolveEntryRows(queries.first.toList()), pageRequest, queries.second.count())
    }

    fun get(ids: List<EntryId>, pageRequest: PageRequest = DefaultPageRequest): Page<S> = transaction {
        if (ids.isEmpty()) Page.empty()
        else {
            val queries = createPagedQuery(pageRequest)
            val idValues = ids.map { it.value }
            Page.of(
                resolveEntryRows(queries.first.combine { Entries.id inList idValues }.toList()),
                pageRequest,
                queries.second.combine { Entries.id inList idValues }.count()
            )
        }
    }

    protected fun resolveEntryRows(rows: List<ResultRow>): List<S> {
        val groups = getGroupsForEntries(rows.map { it[Entries.id] })
        return rows.map { toSlimModel(it, groups.getOrDefault(it[Entries.id], GroupSet())) }
    }

    private fun createPagedQuery(pageRequest: PageRequest): Pair<Query, Query> {
        // slice to only query columns required for slim entry
        var baseQuery = getBaseQuery(Entries).adjustSelect { select(slimColumnSet + Entries.type) }
        val subtrees = groupSetService.subtrees(pageRequest.tags, pageRequest.collections)

        if (subtrees.tags.isNotEmpty()) {
            val tagIds = subtrees.tags.map { it.id }
            baseQuery = baseQuery.combine {
                exists(EntryGroups.selectAll().where {
                    (EntryGroups.entryId eq Entries.id) and (EntryGroups.groupId inList tagIds)
                })
            }
        }
        if (subtrees.collections.isNotEmpty()) {
            val collectionIds = subtrees.collections.map { it.id }
            baseQuery = baseQuery.combine {
                exists(EntryGroups.selectAll().where {
                    (EntryGroups.entryId eq Entries.id) and (EntryGroups.groupId inList collectionIds)
                })
            }
        }
        if (pageRequest.source != null) {
            // wildcard search to use like operator
            val predicate = if(pageRequest.source.contains('%')) {
                Entries.src like pageRequest.source.lowercase()
            } else {
                Entries.src like pageRequest.source.lowercase()
            }
            baseQuery = baseQuery.combine { predicate }
        }

        baseQuery = if (pageRequest.direction == SortDirection.RAND) {
            baseQuery.orderBy(Random())
        } else {
            val sortColumn = Entries.findColumn(pageRequest.sort) ?: Entries.dateUpdated
            val sortOrder = pageRequest.direction ?: SortDirection.DESC
            baseQuery.orderBy(sortColumn, sortOrder)
        }

        return Pair(baseQuery.copy().apply {
            limit(pageRequest.size)
            offset(max(0, (pageRequest.page - 1) * pageRequest.size))
        }, baseQuery)
    }

    open fun add(entry: U): T {
        val serviceName = this::class.simpleName
        return transaction {
            val newId = newEntryId()
            groupSetService.assertGroups(entry.tags, entry.collections)
            Entries.insert(toInsert(newId, entry))
            for (group in entry.tags + entry.collections) {
                EntryGroups.insert {
                    it[groupId] = group
                    it[entryId] = newId.value
                }
            }
            entryAuditService.acceptAuditEvent(newId, serviceName, "Created")
            postprocess(newId, entry)
        }
    }

    open fun update(entry: U, newVersion: Boolean = true): T? {
        val id = entry.id
        return if (id == null) {
            add(entry)
        } else {
            groupSetService.assertGroups(entry.tags, entry.collections)
            val serviceName = this::class.simpleName
            transaction {
                val where = getBaseQuery().combine { Entries.id eq id.value }.where
                    ?: throw IllegalStateException("Missing where clause for entry update id=${id.value}")
                val updated = Entries.update({ where }, body = {
                    toUpdate(entry)(it)
                    if(newVersion) {
                        it.update(version, version + 1)
                    }
                })
                if (updated > 0) {
                    updateGroupsForEntry(entry.tags + entry.collections, id)
                    val updatedEntry = postprocess(id, entry)
                    if (newVersion) {
                        entryAuditService.acceptAuditEvent(
                            id, serviceName,
                            "Updated to version ${updatedEntry.version}"
                        )
                    }
                    updatedEntry
                } else {
                    null
                }
            }
        }
    }

    fun update(entry: T, newVersion: Boolean = false): T? {
        val serviceName = this::class.simpleName
        return transaction {
            groupSetService.assertGroups(entry.tags.map { it.id }, entry.collections.map { it.id })
            val where = getBaseQuery().combine { Entries.id eq entry.id.value }.where
                ?: throw IllegalStateException("Missing where clause for entry update id=${entry.id.value}")
            val updated = Entries.update({ where }, body = {
                toUpdate(entry)(it)
                if (newVersion) {
                    it[dateUpdated] = OffsetDateTime.now(ZoneOffset.UTC)
                    it.update(version, version + 1)
                }
            })
            if (updated > 0) {
                updateGroupsForEntry(entry.tags.map { it.id } + entry.collections.map { it.id }, entry.id)
                val updatedEntry = get(entry.id)
                if (newVersion) {
                    entryAuditService.acceptAuditEvent(
                        entry.id, serviceName,
                        "Updated to version ${updatedEntry?.version}"
                    )
                }
                updatedEntry
            } else {
                null
            }
        }
    }

    fun mergeProps(id: EntryId, props: BaseProperties): Unit = transaction {
        val row = getBaseQuery().adjustSelect { select(Entries.props) }
            .combine { Entries.id eq id.value }
            .singleOrNull()
        row?.also {
            val where = getBaseQuery().combine { Entries.id eq id.value }.where
                ?: throw IllegalStateException("Missing where clause for entry props update id=${id.value}")
            val originalProps = row[Entries.props] ?: BaseProperties()
            val newProps = originalProps.merge(props)
            Entries.update({ where }) {
                it[Entries.props] = newProps
            }
        }
    }

    open fun delete(id: EntryId): Boolean = transaction {
        val entry = getBaseQuery().adjustSelect { this.select(Entries.type) }
            .combine { Entries.id eq id.value }
            .singleOrNull()

        entry?.let {
            Entries.deleteWhere { Entries.id eq id.value } > 0 && resourceManager.deleteAll(id)
        } ?: false
    }

    protected fun updateGroupsForEntry(groups: List<String>, id: EntryId) {
        val currentGroups = getGroupsForEntry(id).run { tags.map { it.id } + collections.map { it.id } }
        val newGroups = groups.toSet()

        currentGroups.filterNot { newGroups.contains(it) }
            .forEach { gid ->
                EntryGroups.deleteWhere {
                    EntryGroups.entryId eq id.value and (EntryGroups.groupId eq gid)
                }
            }

        newGroups.filterNot { currentGroups.contains(it) }
            .forEach { group ->
                EntryGroups.insert {
                    it[groupId] = group
                    it[entryId] = id.value
                }
            }
    }

    private fun getGroupsForEntries(ids: List<String>): Map<String, GroupSet> {
        return EntryGroups.selectAll().where { EntryGroups.entryId.inList(ids) }
            .groupBy { it[EntryGroups.entryId] }
            .mapValues { entry ->
                val groupIds = entry.value.map { it[EntryGroups.groupId] }
                groupSetService.getIn(groupIds)
            }
    }

    private fun getGroupsForEntry(id: EntryId): GroupSet {
        val groupIds = EntryGroups.select(EntryGroups.groupId)
            .where { EntryGroups.entryId eq id.value }
            .map { it[EntryGroups.groupId] }
        return groupSetService.getIn(groupIds)
    }

    protected open fun postprocess(eid: EntryId, entry: U) : T =
        get(eid) ?: throw IllegalStateException("Entry ${eid.value} not found after postprocess")

    protected abstract fun getBaseQuery(base: ColumnSet = Entries, where: BaseEntries = Entries): Query

    protected abstract val slimColumnSet: List<Expression<*>>

    protected abstract fun toInsert(eId: EntryId, entry: U): BaseEntries.(InsertStatement<*>) -> Unit

    protected abstract fun toUpdate(entry: U): BaseEntries.(UpdateBuilder<*>) -> Unit

    protected abstract fun toUpdate(entry: T): BaseEntries.(UpdateBuilder<*>) -> Unit

    protected abstract fun toModel(row: ResultRow, groups: GroupSet = GroupSet(), table: BaseEntries = Entries): T

    protected abstract fun toSlimModel(row: ResultRow, groups: GroupSet = GroupSet(), table: BaseEntries = Entries): S

    protected fun toModel(row: ResultRow, table: BaseEntries = Entries): T {
        return toModel(row, getGroupsForEntry(EntryId(row[table.id])), table)
    }

}
