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
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.statements.jdbc.JdbcConnectionImpl
import org.jetbrains.exposed.v1.jdbc.statements.jdbc.JdbcResult
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.sql.Connection
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

    override fun getBaseQuery(base: ColumnSet, where: BaseEntries): Query {
        return base.selectAll()
    }

    override val slimColumnSet: List<Expression<*>> = listOf(
        Entries.id, Entries.title, Entries.src, Entries.dateUpdated, Entries.content,
        Entries.starred, Entries.thumbnailId, Entries.read
    )

    @Deprecated("EntryService does not support insert/update via the generic path", level = DeprecationLevel.ERROR)
    override fun toInsert(eId: EntryId, entry: NewEntry): BaseEntries.(UpdateBuilder<*>) -> Unit =
        throw NotImplementedError("EntryService.toInsert is unreachable - use a type-specific service")

    @Deprecated("EntryService does not support insert/update via the generic path", level = DeprecationLevel.ERROR)
    override fun toNewEntry(entry: Entry): NewEntry =
        throw NotImplementedError("EntryService.toNewEntry is unreachable - use a type-specific service")

    @Deprecated("EntryService does not support insert/update via the generic path", level = DeprecationLevel.ERROR)
    override fun toUpdate(entry: NewEntry): BaseEntries.(UpdateBuilder<*>) -> Unit =
        throw NotImplementedError("EntryService.toUpdate(NewEntry) is unreachable - use a type-specific service")

    @Deprecated("EntryService does not support insert/update via the generic path", level = DeprecationLevel.ERROR)
    override fun toUpdate(entry: Entry): BaseEntries.(UpdateBuilder<*>) -> Unit =
        throw NotImplementedError("EntryService.toUpdate(Entry) is unreachable - use a type-specific service")

    fun suggest(term: String, page: PageRequest = DefaultPageRequest): Page<SlimEntry> {
        if (term.isBlank()) return Page.empty()
        return transaction {
            val pattern = "${term.lowercase()}%"
            val baseQuery = getBaseQuery(Entries)
                .adjustSelect { select(slimColumnSet + Entries.type) }
                .combine { Entries.title.lowerCase() like pattern }
                .orderBy(Entries.dateUpdated, SortOrder.DESC)
            val entries = baseQuery.copy().apply {
                limit(page.size)
                offset(max(0, (page.page - 1) * page.size))
            }.toList().let { resolveEntryRows(it) }
            val count = baseQuery.count()
            Page.of(entries, page, count)
        }
    }

    fun search(term: String, page: PageRequest = DefaultPageRequest): Page<SlimEntry> {
        if (term.isBlank()) return Page.empty()
        return transaction {
            val conn = (TransactionManager.current().connection as JdbcConnectionImpl).connection
            runPostgresSearchQuery(conn, term, page)
        }
    }

    private fun runPostgresSearchQuery(conn: Connection, term: String, page: PageRequest): Page<SlimEntry> {
        val columns = slimColumnSet + Entries.type
        val columnSelect = columns.joinToString(", ") { (it as Column<*>).name }
        val andWhere = if (page.source != null) " AND ${Entries.src.name} LIKE ?" else ""
        val baseSql = """
                    FROM ${Entries.tableName}, websearch_to_tsquery('english', ?) query_ts
                    WHERE TS_DOC @@ query_ts $andWhere
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

        val entries = conn.prepareStatement(searchSql).use { prep ->
            prep.setString(1, term)
            if (page.source != null) prep.setString(2, page.source)
            prep.executeQuery().use { set ->
                val fieldMap = columns.mapIndexed { index, expression -> expression to index }.toMap()
                val resultRows = mutableListOf<ResultRow>()
                while (set.next()) {
                    resultRows.add(ResultRow.create(JdbcResult(set), fieldMap))
                }
                resolveEntryRows(resultRows)
            }
        }
        val countSql = """
                    SELECT COUNT(*)
                    $baseSql
                """.trimIndent()
        val count = conn.prepareStatement(countSql).use { prep ->
            prep.setString(1, term)
            if (page.source != null) prep.setString(2, page.source)
            prep.executeQuery().use { set ->
                set.next()
                set.getLong(1)
            }
        }
        return Page.of(entries, page, count)
    }

    fun star(id: EntryId, starred: Boolean): Entry? = transaction {
        val updated = Entries.update({ Entries.id eq id.value }) {
            it[Entries.starred] = starred
        }
        if (updated > 0) {
            val starMessage = if (starred) "starred" else "unstarred"
            entryAuditService.acceptAuditEvent(id, EntryService::class.simpleName, "Entry $starMessage")
            get(id)
        } else {
            null
        }
    }

    fun getEntryVersions(id: EntryId): List<EntryVersion> = transaction {
        EntryVersions.select(EntryVersions.id, EntryVersions.version, EntryVersions.dateUpdated)
            .where { EntryVersions.id eq id.value }
            .orderBy(EntryVersions.version, SortOrder.ASC)
            .map {
                EntryVersion(
                    id = EntryId(it[EntryVersions.id]),
                    version = it[EntryVersions.version],
                    dateUpdated = it[EntryVersions.dateUpdated].toInstant()
                )
            }
    }

    fun updateEntryGroups(entryId: EntryId, tagIds: List<String>, collectionIds: List<String>): Boolean {
        groupSetService.assertGroups(tagIds, collectionIds)
        if (get(entryId) == null) return false
        transaction {
            updateGroupsForEntry(tagIds + collectionIds, entryId)
        }
        return true
    }
}
