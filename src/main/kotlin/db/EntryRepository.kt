package db

import common.*
import common.page.DefaultPageRequest
import common.page.Page
import common.page.PageRequest
import common.page.SortDirection
import entry.EntryAuditService
import group.EntryGroups
import group.GroupSet
import group.GroupSetService
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import resource.ResourceManager
import util.RandomUtils
import util.combine
import util.findColumn
import util.orderBy
import kotlin.math.max

abstract class EntryRepository<T : Entry, S : SlimEntry, U : NewEntry>(
    private val groupSetService: GroupSetService,
    protected val entryAuditService: EntryAuditService,
    protected val resourceManager: ResourceManager
) {

    fun get(id: String): T? = transaction {
        getBaseQuery().combine { Entries.id eq id }
            .mapNotNull { toModel(it) }
            .singleOrNull()
    }

    fun get(id: String, version: Int): T? = transaction {
        getBaseQuery(EntryVersions, EntryVersions).combine {
            EntryVersions.id eq id and
                (EntryVersions.version eq version)
        }
            .mapNotNull { toModel(it, EntryVersions) }
            .singleOrNull()
    }

    fun get(pageRequest: PageRequest = DefaultPageRequest): Page<S> = transaction {
        val queries = createPagedQuery(pageRequest)
        Page.of(resolveEntryRows(queries.first.toList()), pageRequest, queries.second.count())
    }

    fun get(ids: List<String>, pageRequest: PageRequest = DefaultPageRequest): Page<S> = transaction {
        if (ids.isEmpty()) Page.empty()
        else {
            val queries = createPagedQuery(pageRequest)
            Page.of(
                resolveEntryRows(queries.first.combine { Entries.id inList ids }.toList()),
                pageRequest,
                queries.second.combine { Entries.id inList ids }.count()
            )
        }
    }

    private fun resolveEntryRows(rows: List<ResultRow>): List<S> {
        val groups = getGroupsForEntries(rows.map { it[Entries.id] })
        return rows.map { toSlimModel(it, groups.getOrDefault(it[Entries.id], GroupSet())) }
    }

    private fun createPagedQuery(pageRequest: PageRequest): Pair<Query, Query> {
        var table: ColumnSet = Entries

        val tagTable = EntryGroups.alias("tags")
        val collectionTable = EntryGroups.alias("collections")
        if (pageRequest.tag != null) {
            table = table.innerJoin(tagTable, { Entries.id }, { tagTable[EntryGroups.entryId] })
        }
        if (pageRequest.collection != null) {
            table = table.innerJoin(collectionTable, { Entries.id }, { collectionTable[EntryGroups.entryId] })
        }

        var baseQuery = getBaseQuery(table)
        val subtrees = groupSetService.subtrees(pageRequest.tag, pageRequest.collection)

        if (subtrees.tags.isNotEmpty()) {
            baseQuery = baseQuery.combine { tagTable[EntryGroups.groupId].inList(subtrees.tags.map { it.id }) }
        }
        if (subtrees.collections.isNotEmpty()) {
            baseQuery = baseQuery.combine { collectionTable[EntryGroups.groupId].inList(subtrees.collections.map { it.id }) }
        }

        val sortColumn = Entries.findColumn(pageRequest.sort) ?: Entries.dateUpdated
        val sortOrder = pageRequest.direction ?: SortDirection.DESC

        return Pair(baseQuery.copy().apply {
            orderBy(sortColumn, sortOrder)
            distinct
            limit(pageRequest.size, max(0, (pageRequest.page - 1) * pageRequest.size))
        }, baseQuery)
    }

    open fun add(entry: U): T {
        val serviceName = this::class.simpleName
        return transaction {
            val newId = RandomUtils.generateUid()
            groupSetService.assertGroups(entry.tags, entry.collections)
            Entries.insert(toInsert(newId, entry))
            for (group in entry.tags + entry.collections) {
                EntryGroups.insert {
                    it[groupId] = group
                    it[entryId] = newId
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
            transaction {
                val where = getBaseQuery().combine { Entries.id eq id }.where!!
                val updated = Entries.update({ where }, body = {
                    toUpdate(entry)(it)
                    if(newVersion) {
                        with(SqlExpressionBuilder) {
                            it.update(version, version + 1)
                        }
                    }
                })
                if (updated > 0) {
                    updateGroupsForEntry(entry.tags + entry.collections, id)
                    val updatedEntry = postprocess(id, entry)
                    if (newVersion) {
                        entryAuditService.acceptAuditEvent(
                            id, this::class.simpleName,
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

    fun update(entry: T, newVersion: Boolean = false): T? = transaction {
        groupSetService.assertGroups(entry.tags.map { it.id }, entry.collections.map { it.id })
        val where = getBaseQuery().combine { Entries.id eq entry.id }.where!!
        val updated = Entries.update({ where }, body = {
            toUpdate(entry)(it)
            if (newVersion) {
                it[dateUpdated] = System.currentTimeMillis()
                with(SqlExpressionBuilder) {
                    it.update(version, version + 1)
                }
            }
        })
        if (updated > 0) {
            updateGroupsForEntry(entry.tags.map { it.id } + entry.collections.map { it.id }, entry.id)
            val updatedEntry = get(entry.id)
            if (newVersion) {
                entryAuditService.acceptAuditEvent(
                    id, this::class.simpleName,
                    "Updated to version ${updatedEntry?.version}"
                )
            }
            updatedEntry
        } else {
            null
        }
    }

    fun mergeProps(id: String, props: BaseProperties): Unit = transaction {
        val row = getBaseQuery().adjustSlice { slice(Entries.props) }
            .combine { Entries.id eq id }
            .singleOrNull()
        row?.also {
            val where = getBaseQuery().combine { Entries.id eq id }.where!!
            val originalProps = row[Entries.props] ?: BaseProperties()
            val newProps = originalProps.merge(props)
            Entries.update({ where }) {
                it[Entries.props] = newProps
            }
        }
    }

    open fun delete(id: String): Boolean = transaction {
        val entry = getBaseQuery().adjustSlice { this.slice(Entries.type) }
            .combine { Entries.id eq id }
            .singleOrNull()

        entry?.let {
            Entries.deleteWhere { Entries.id eq id } > 0 && resourceManager.deleteAll(id)
        } ?: false
    }

    private fun updateGroupsForEntry(groups: List<String>, id: String) {
        val currentGroups = getGroupsForEntry(id).run { tags.map { it.id } + collections.map { it.id } }
        val newGroups = groups.toSet()

        currentGroups.filterNot { newGroups.contains(it) }
            .forEach {
                EntryGroups.deleteWhere {
                    EntryGroups.entryId eq id and (EntryGroups.groupId eq it)
                }
            }

        newGroups.filterNot { currentGroups.contains(it) }
            .forEach { group ->
                EntryGroups.insert {
                    it[groupId] = group
                    it[entryId] = id
                }
            }
    }

    private fun getGroupsForEntries(ids: List<String>): Map<String, GroupSet> {
        return EntryGroups.select { EntryGroups.entryId.inList(ids) }
            .groupBy { it[EntryGroups.entryId] }
            .mapValues { entry ->
                val groupIds = entry.value.map { it[EntryGroups.groupId] }
                groupSetService.getIn(groupIds)
            }
    }

    private fun getGroupsForEntry(id: String): GroupSet {
        val groupIds = EntryGroups.slice(EntryGroups.groupId)
            .select { EntryGroups.entryId eq id }
            .map { it[EntryGroups.groupId] }
        return groupSetService.getIn(groupIds)
    }

    protected open fun postprocess(eid: String, entry: U) : T = get(eid)!!

    protected abstract fun getBaseQuery(base: ColumnSet = Entries, where: BaseEntries = Entries): Query

    protected abstract fun toInsert(eId: String, entry: U): BaseEntries.(InsertStatement<*>) -> Unit

    protected abstract fun toUpdate(entry: U): BaseEntries.(UpdateBuilder<*>) -> Unit

    protected abstract fun toUpdate(entry: T): BaseEntries.(UpdateBuilder<*>) -> Unit

    protected abstract fun toModel(row: ResultRow, groups: GroupSet, table: BaseEntries = Entries): T

    protected abstract fun toSlimModel(row: ResultRow, groups: GroupSet, table: BaseEntries = Entries): S

    protected fun toModel(row: ResultRow, table: BaseEntries = Entries): T {
        return toModel(row, getGroupsForEntry(row[table.id]), table)
    }

}
