package db

import common.*
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

abstract class EntryRepository<T : Entry, S : SlimEntry, in U : NewEntry>(
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

    fun get(page: PageRequest = DefaultPageRequest): List<S> = transaction {
        resolveEntryRows(createPagedQuery(page).toList())
    }

    fun get(ids: List<String>, page: PageRequest = DefaultPageRequest): List<S> = transaction {
        if (ids.isEmpty()) emptyList()
        else {
            resolveEntryRows(createPagedQuery(page).combine { Entries.id inList ids }.toList())
        }
    }

    private fun resolveEntryRows(rows: List<ResultRow>): List<S> {
        val groups = getGroupsForEntries(rows.map { it[Entries.id] })
        return rows.map { toSlimModel(it, groups.getOrDefault(it[Entries.id], GroupSet())) }
    }

    private fun createPagedQuery(page: PageRequest): Query {
        var table: ColumnSet = Entries

        val tagTable = EntryGroups.alias("tags")
        val collectionTable = EntryGroups.alias("collections")
        if (page.tag != null) {
            table = table.innerJoin(tagTable, { Entries.id }, { tagTable[EntryGroups.entryId] })
        }
        if (page.collection != null) {
            table = table.innerJoin(collectionTable, { Entries.id }, { collectionTable[EntryGroups.entryId] })
        }

        var query = getBaseQuery(table)
        val subtrees = groupSetService.subtrees(page.tag, page.collection)

        if(subtrees.tags.isNotEmpty()) {
            query = query.combine { tagTable[EntryGroups.groupId].inList(subtrees.tags.map { it.id }) }
        }
        if(subtrees.collections.isNotEmpty()) {
            query = query.combine { collectionTable[EntryGroups.groupId].inList(subtrees.collections.map { it.id }) }
        }

        val sortColumn = Entries.findColumn(page.sort) ?: Entries.dateUpdated
        val sortOrder = page.direction ?: SortDirection.DESC

        return query.apply {
            orderBy(sortColumn, sortOrder)
            limit(page.limit, page.offset)
        }
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
            get(newId)!!
        }
    }

    open fun update(entry: U): T? {
        val id = entry.id
        return if (id == null) {
            add(entry)
        } else {
            groupSetService.assertGroups(entry.tags, entry.collections)
            val serviceName = this::class.simpleName
            transaction {
                val where = getBaseQuery().combine { Entries.id eq id }.where!!
                val updated = Entries.update({ where }, body = {
                    toUpdate(entry)(it)
                    with(SqlExpressionBuilder) {
                        it.update(version, version + 1)
                    }
                })
                if (updated > 0) {
                    updateGroupsForEntry(entry.tags + entry.collections, id)
                    val updatedEntry = get(id)
                    entryAuditService.acceptAuditEvent(
                        id, serviceName,
                        "Updated to version ${updatedEntry?.version}"
                    )
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
            if (newVersion)
                entryAuditService.acceptAuditEvent(
                    id,
                    this::class.simpleName,
                    "Updated to version ${updatedEntry?.version}"
                )
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