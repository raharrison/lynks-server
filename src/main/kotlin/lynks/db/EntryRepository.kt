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

    fun get(userId: UserId, id: EntryId): T? = transaction {
        baseQuery(userId).combine { Entries.id eq id.value }
            .mapNotNull { toModel(userId, it) }
            .singleOrNull()
    }

    fun get(userId: UserId, id: EntryId, version: Int): T? = transaction {
        baseQuery(userId, EntryVersions, EntryVersions).combine {
            EntryVersions.id eq id.value and
                (EntryVersions.version eq version)
        }
            .mapNotNull { toModel(userId, it, EntryVersions) }
            .singleOrNull()
    }

    fun get(userId: UserId, pageRequest: PageRequest = DefaultPageRequest): Page<S> = transaction {
        val queries = createPagedQuery(userId, pageRequest)
        Page.of(resolveEntryRows(userId, queries.first.toList()), pageRequest, queries.second.count())
    }

    fun get(userId: UserId, ids: List<EntryId>, pageRequest: PageRequest = DefaultPageRequest): Page<S> = transaction {
        if (ids.isEmpty()) Page.empty()
        else {
            val queries = createPagedQuery(userId, pageRequest)
            val idValues = ids.map { it.value }
            Page.of(
                resolveEntryRows(userId, queries.first.combine { Entries.id inList idValues }.toList()),
                pageRequest,
                queries.second.combine { Entries.id inList idValues }.count()
            )
        }
    }

    protected fun resolveEntryRows(userId: UserId, rows: List<ResultRow>): List<S> {
        val groups = getGroupsForEntries(userId, rows.map { it[Entries.id] })
        return rows.map { toSlimModel(it, groups.getOrDefault(it[Entries.id], GroupSet())) }
    }

    private fun createPagedQuery(userId: UserId, pageRequest: PageRequest): Pair<Query, Query> {
        // slice to only query columns required for slim entry
        var baseQuery = baseQuery(userId).adjustSelect { select(slimColumnSet + Entries.type) }
        val subtrees = groupSetService.subtrees(userId, pageRequest.tags, pageRequest.collections)

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
            baseQuery = baseQuery.combine { Entries.src like pageRequest.source.lowercase() }
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

    open fun add(userId: UserId, entry: U): T {
        val serviceName = this::class.simpleName
        return transaction {
            val newId = newEntryId()
            groupSetService.assertGroups(userId, entry.tags, entry.collections)
            val insert = toInsert(userId, newId, entry)
            Entries.insert {
                insert(it)
                it[Entries.userId] = userId.value
            }
            for (group in entry.tags + entry.collections) {
                EntryGroups.insert {
                    it[groupId] = group
                    it[entryId] = newId.value
                }
            }
            entryAuditService.acceptAuditEvent(newId, serviceName, "Created")
            postprocess(userId, newId, entry)
        }
    }

    open fun update(userId: UserId, entry: U, newVersion: Boolean = true): T? {
        val id = entry.id
        return if (id == null) {
            add(userId, entry)
        } else {
            groupSetService.assertGroups(userId, entry.tags, entry.collections)
            val serviceName = this::class.simpleName
            transaction {
                val query = baseQuery(userId).combine { Entries.id eq id.value }
                // toUpdate can attach pasted images to the entry, so it must not run for an entry the user does not own
                if (query.empty()) return@transaction null
                val where = query.where
                    ?: throw IllegalStateException("Missing where clause for entry update id=${id.value}")
                val body = toUpdate(userId, entry)
                Entries.update({ where }, body = {
                    body(it)
                    if (newVersion) {
                        it.update(version, version + 1)
                    }
                })
                updateGroupsForEntry(userId, entry.tags + entry.collections, id)
                val updatedEntry = postprocess(userId, id, entry)
                if (newVersion) {
                    entryAuditService.acceptAuditEvent(
                        id, serviceName,
                        "Updated to version ${updatedEntry.version}"
                    )
                }
                updatedEntry
            }
        }
    }

    fun update(userId: UserId, entry: T, newVersion: Boolean = false): T? {
        val serviceName = this::class.simpleName
        return transaction {
            groupSetService.assertGroups(userId, entry.tags.map { it.id }, entry.collections.map { it.id })
            val where = baseQuery(userId).combine { Entries.id eq entry.id.value }.where
                ?: throw IllegalStateException("Missing where clause for entry update id=${entry.id.value}")
            val body = toUpdate(userId, entry)
            val updated = Entries.update({ where }, body = {
                body(it)
                if (newVersion) {
                    it[dateUpdated] = OffsetDateTime.now(ZoneOffset.UTC)
                    it.update(version, version + 1)
                }
            })
            if (updated > 0) {
                updateGroupsForEntry(userId, entry.tags.map { it.id } + entry.collections.map { it.id }, entry.id)
                val updatedEntry = get(userId, entry.id)
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

    // Groups are not versioned, so the entry keeps its current tags and collections
    fun revert(userId: UserId, id: EntryId, version: Int): T? {
        val previous = get(userId, id, version) ?: return null
        return update(userId, toNewEntry(userId, previous), newVersion = true)?.also {
            entryAuditService.acceptAuditEvent(id, this::class.simpleName, "Reverted to version $version")
        }
    }

    fun mergeProps(userId: UserId, id: EntryId, props: BaseProperties): Unit = transaction {
        val query = baseQuery(userId).combine { Entries.id eq id.value }
        val row = query.copy().adjustSelect { select(Entries.props) }.singleOrNull()
        row?.also {
            val where = query.where
                ?: throw IllegalStateException("Missing where clause for entry props update id=${id.value}")
            val originalProps = row[Entries.props] ?: BaseProperties()
            val newProps = originalProps.merge(props)
            Entries.update({ where }) {
                it[Entries.props] = newProps
            }
        }
    }

    open fun delete(userId: UserId, id: EntryId): Boolean = transaction {
        val where = baseQuery(userId).combine { Entries.id eq id.value }.where
            ?: throw IllegalStateException("Missing where clause for entry delete id=${id.value}")
        Entries.deleteWhere { where } > 0 && resourceManager.deleteAll(id)
    }

    protected fun updateGroupsForEntry(userId: UserId, groups: List<String>, id: EntryId) {
        val currentGroups = getGroupsForEntry(userId, id).run { tags.map { it.id } + collections.map { it.id } }
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

    private fun getGroupsForEntries(userId: UserId, ids: List<String>): Map<String, GroupSet> {
        return EntryGroups.selectAll().where { EntryGroups.entryId.inList(ids) }
            .groupBy { it[EntryGroups.entryId] }
            .mapValues { entry ->
                val groupIds = entry.value.map { it[EntryGroups.groupId] }
                groupSetService.getIn(userId, groupIds)
            }
    }

    private fun getGroupsForEntry(userId: UserId, id: EntryId): GroupSet {
        val groupIds = EntryGroups.select(EntryGroups.groupId)
            .where { EntryGroups.entryId eq id.value }
            .map { it[EntryGroups.groupId] }
        return groupSetService.getIn(userId, groupIds)
    }

    protected open fun postprocess(userId: UserId, eid: EntryId, entry: U): T =
        get(userId, eid) ?: throw IllegalStateException("Entry ${eid.value} not found after postprocess")

    // The only way to start an entry query, so every read and write is confined to the caller's entries
    protected fun baseQuery(userId: UserId, base: ColumnSet = Entries, table: BaseEntries = Entries): Query {
        val owned = table.userId eq userId.value
        val condition = typeCondition(table)?.let { owned and it } ?: owned
        return base.selectAll().where(condition)
    }

    protected abstract fun typeCondition(table: BaseEntries): Op<Boolean>?

    protected abstract val slimColumnSet: List<Expression<*>>

    protected abstract fun toInsert(userId: UserId, eId: EntryId, entry: U): BaseEntries.(InsertStatement<*>) -> Unit

    protected abstract fun toUpdate(userId: UserId, entry: U): BaseEntries.(UpdateBuilder<*>) -> Unit

    protected abstract fun toUpdate(userId: UserId, entry: T): BaseEntries.(UpdateBuilder<*>) -> Unit

    protected abstract fun toNewEntry(userId: UserId, entry: T): U

    protected abstract fun toModel(row: ResultRow, groups: GroupSet = GroupSet(), table: BaseEntries = Entries): T

    protected abstract fun toSlimModel(row: ResultRow, groups: GroupSet = GroupSet(), table: BaseEntries = Entries): S

    protected fun toModel(userId: UserId, row: ResultRow, table: BaseEntries = Entries): T {
        return toModel(row, getGroupsForEntry(userId, EntryId(row[table.id])), table)
    }

}
