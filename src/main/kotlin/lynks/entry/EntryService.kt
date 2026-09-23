package lynks.entry

import lynks.common.*
import lynks.common.page.DefaultPageRequest
import lynks.common.page.Page
import lynks.common.page.PageRequest
import lynks.common.page.SortDirection
import lynks.db.EntryRepository
import lynks.group.GroupSet
import lynks.group.GroupSetService
import lynks.resource.ResourceManager
import lynks.util.combine
import lynks.util.findColumn
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.statements.jdbc.JdbcConnectionImpl
import org.jetbrains.exposed.v1.jdbc.statements.jdbc.JdbcResult
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.sql.Connection
import java.sql.PreparedStatement
import kotlin.math.max

class EntryService(
    groupSetService: GroupSetService, entryAuditService: EntryAuditService, resourceManager: ResourceManager
) :
    EntryRepository<Entry, SlimEntry, NewEntry>(groupSetService, entryAuditService, resourceManager) {

    override fun toModel(row: ResultRow, groups: GroupSet, table: BaseEntries): Entry {
        return when (row[table.type]) {
            EntryType.LINK -> RowMapper.toLink(table, row, groups.tags, groups.collections)
            EntryType.NOTE -> RowMapper.toNote(table, row, groups.tags, groups.collections)
            EntryType.SNIPPET -> RowMapper.toSnippet(table, row, groups.tags, groups.collections)
            EntryType.FILE -> RowMapper.toFile(table, row, groups.tags, groups.collections)
        }
    }

    override fun toSlimModel(row: ResultRow, groups: GroupSet, table: BaseEntries): SlimEntry {
        return when (row[table.type]) {
            EntryType.LINK -> RowMapper.toSlimLink(table, row, groups.tags, groups.collections)
            EntryType.NOTE -> RowMapper.toSlimNote(table, row, groups.tags, groups.collections)
            EntryType.SNIPPET -> RowMapper.toSlimSnippet(table, row, groups.tags, groups.collections)
            EntryType.FILE -> RowMapper.toSlimFile(table, row, groups.tags, groups.collections)
        }
    }

    override fun typeCondition(table: BaseEntries): Op<Boolean>? = null

    override val slimColumnSet: List<Expression<*>> = listOf(
        Entries.id, Entries.title, Entries.src, Entries.dateUpdated, Entries.content,
        Entries.starred, Entries.thumbnailId, Entries.read
    )

    @Deprecated("EntryService does not support insert/update via the generic path", level = DeprecationLevel.ERROR)
    override fun toInsert(userId: UserId, eId: EntryId, entry: NewEntry): BaseEntries.(UpdateBuilder<*>) -> Unit =
        throw NotImplementedError("EntryService.toInsert is unreachable - use a type-specific service")

    @Deprecated("EntryService does not support insert/update via the generic path", level = DeprecationLevel.ERROR)
    override fun toNewEntry(userId: UserId, entry: Entry): NewEntry =
        throw NotImplementedError("EntryService.toNewEntry is unreachable - use a type-specific service")

    @Deprecated("EntryService does not support insert/update via the generic path", level = DeprecationLevel.ERROR)
    override fun toUpdate(userId: UserId, entry: NewEntry): BaseEntries.(UpdateBuilder<*>) -> Unit =
        throw NotImplementedError("EntryService.toUpdate(NewEntry) is unreachable - use a type-specific service")

    @Deprecated("EntryService does not support insert/update via the generic path", level = DeprecationLevel.ERROR)
    override fun toUpdate(userId: UserId, entry: Entry): BaseEntries.(UpdateBuilder<*>) -> Unit =
        throw NotImplementedError("EntryService.toUpdate(Entry) is unreachable - use a type-specific service")

    fun suggest(userId: UserId, term: String, page: PageRequest = DefaultPageRequest): Page<SlimEntry> {
        if (term.isBlank()) return Page.empty()
        return transaction {
            val pattern = "${term.lowercase()}%"
            val baseQuery = baseQuery(userId)
                .adjustSelect { select(slimColumnSet + Entries.type) }
                .combine { Entries.title.lowerCase() like pattern }
                .orderBy(Entries.dateUpdated, SortOrder.DESC)
            val entries = baseQuery.copy().apply {
                limit(page.size)
                offset(max(0, (page.page - 1) * page.size))
            }.toList().let { resolveEntryRows(userId, it) }
            val count = baseQuery.count()
            Page.of(entries, page, count)
        }
    }

    fun search(userId: UserId, term: String, page: PageRequest = DefaultPageRequest): Page<SlimEntry> {
        if (term.isBlank()) return Page.empty()
        return transaction {
            val conn = (TransactionManager.current().connection as JdbcConnectionImpl).connection
            runPostgresSearchQuery(conn, userId, term, page)
        }
    }

    private fun runPostgresSearchQuery(conn: Connection, userId: UserId, term: String, page: PageRequest): Page<SlimEntry> {
        val columns = slimColumnSet + Entries.type
        val columnSelect = columns.joinToString(", ") { (it as Column<*>).name }
        val andWhere = if (page.source != null) " AND ${Entries.src.name} LIKE ?" else ""
        val baseSql = """
                    FROM ${Entries.tableName}, websearch_to_tsquery('english', ?) query_ts
                    WHERE ${Entries.userId.name} = ? AND TS_DOC @@ query_ts $andWhere
                """.trimIndent()

        val sortOrder = page.direction ?: SortDirection.DESC
        val orderBy = if (sortOrder == SortDirection.RAND) {
            "RANDOM()"
        } else if (page.sort == null || page.sort == "mostRelevant") {
            "ts_rank(TS_DOC, query_ts) ${sortOrder.name}"
        } else {
            val sortColumn = Entries.findColumn(page.sort) ?: Entries.dateUpdated
            "${sortColumn.name} ${sortOrder.name}"
        }
        val searchSql = """
                    SELECT $columnSelect
                    $baseSql
                    ORDER BY $orderBy
                    LIMIT ${page.size} OFFSET ${max(0, (page.page - 1) * page.size)}
                """.trimIndent()

        fun PreparedStatement.bindSearch() {
            setString(1, term)
            setString(2, userId.value)
            if (page.source != null) setString(3, page.source)
        }

        val entries = conn.prepareStatement(searchSql).use { prep ->
            prep.bindSearch()
            prep.executeQuery().use { set ->
                val fieldMap = columns.mapIndexed { index, expression -> expression to index }.toMap()
                val resultRows = mutableListOf<ResultRow>()
                while (set.next()) {
                    resultRows.add(ResultRow.create(JdbcResult(set), fieldMap))
                }
                resolveEntryRows(userId, resultRows)
            }
        }
        val countSql = """
                    SELECT COUNT(*)
                    $baseSql
                """.trimIndent()
        val count = conn.prepareStatement(countSql).use { prep ->
            prep.bindSearch()
            prep.executeQuery().use { set ->
                set.next()
                set.getLong(1)
            }
        }
        return Page.of(entries, page, count)
    }

    fun star(userId: UserId, id: EntryId, starred: Boolean): Entry? = transaction {
        val where = baseQuery(userId).combine { Entries.id eq id.value }.where
            ?: throw IllegalStateException("Missing where clause for entry star update id=${id.value}")
        val updated = Entries.update({ where }) {
            it[Entries.starred] = starred
        }
        if (updated > 0) {
            val starMessage = if (starred) "starred" else "unstarred"
            entryAuditService.acceptAuditEvent(id, EntryService::class.simpleName, "Entry $starMessage")
            get(userId, id)
        } else {
            null
        }
    }

    fun getEntryVersions(userId: UserId, id: EntryId): List<EntryVersion> = transaction {
        EntryVersions.select(EntryVersions.id, EntryVersions.version, EntryVersions.dateUpdated)
            .where { (EntryVersions.id eq id.value) and (EntryVersions.userId eq userId.value) }
            .orderBy(EntryVersions.version, SortOrder.ASC)
            .map {
                EntryVersion(
                    id = EntryId(it[EntryVersions.id]),
                    version = it[EntryVersions.version],
                    dateUpdated = it[EntryVersions.dateUpdated].toInstant()
                )
            }
    }

    fun updateEntryGroups(userId: UserId, entryId: EntryId, tagIds: List<String>, collectionIds: List<String>): Boolean {
        groupSetService.assertGroups(userId, tagIds, collectionIds)
        return transaction {
            if (baseQuery(userId).combine { Entries.id eq entryId.value }.empty()) return@transaction false
            updateGroupsForEntry(userId, tagIds + collectionIds, entryId)
            true
        }
    }
}
