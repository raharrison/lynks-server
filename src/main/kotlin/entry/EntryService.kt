package entry

import common.*
import common.page.DefaultPageRequest
import common.page.Page
import common.page.PageRequest
import db.DatabaseDialect
import db.EntryRepository
import group.GroupSet
import group.GroupSetService
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.statements.jdbc.JdbcConnectionImpl
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import resource.ResourceManager

class EntryService(
    groupSetService: GroupSetService, entryAuditService: EntryAuditService, resourceManager: ResourceManager
) :
    EntryRepository<Entry, SlimEntry, NewEntry>(groupSetService, entryAuditService, resourceManager) {

    override fun toModel(row: ResultRow, groups: GroupSet, table: BaseEntries): Entry {
        return when (row[table.type]) {
            EntryType.LINK -> RowMapper.toLink(table, row, groups.tags, groups.collections)
            EntryType.NOTE -> RowMapper.toNote(table, row, groups.tags, groups.collections)
        }
    }

    override fun toSlimModel(row: ResultRow, groups: GroupSet, table: BaseEntries): SlimEntry {
        return when (row[table.type]) {
            EntryType.LINK -> RowMapper.toSlimLink(table, row, groups.tags, groups.collections)
            EntryType.NOTE -> RowMapper.toSlimNote(table, row, groups.tags, groups.collections)
        }
    }

    override fun getBaseQuery(base: ColumnSet, where: BaseEntries): Query {
        return base.selectAll()
    }

    override fun toInsert(eId: String, entry: NewEntry): BaseEntries.(UpdateBuilder<*>) -> Unit {
        throw NotImplementedError()
    }

    override fun toUpdate(entry: NewEntry): BaseEntries.(UpdateBuilder<*>) -> Unit {
        throw NotImplementedError()
    }

    override fun toUpdate(entry: Entry): BaseEntries.(UpdateBuilder<*>) -> Unit {
        throw NotImplementedError()
    }

    fun search(term: String, page: PageRequest = DefaultPageRequest): Page<SlimEntry> = transaction {
        val conn = (TransactionManager.current().connection as JdbcConnectionImpl).connection
        if(Environment.database.dialect == DatabaseDialect.H2) {
            conn.prepareStatement("SELECT * FROM FT_SEARCH_DATA(?, 0, 0)").use { prep ->
                prep.setString(1, term)
                prep.executeQuery().use { set ->
                    val keys = mutableListOf<String>()
                    while (set.next()) {
                        val res = set.getArray("KEYS")
                        (res.array as Array<*>).forEach { keys.add(it.toString()) }
                    }
                    get(keys, page)
                }
            }
        } else {
            conn.prepareStatement("SELECT ID FROM ENTRY WHERE TITLE LIKE ? OR PLAIN_CONTENT LIKE ?").use { prep ->
                prep.setString(1, "%$term%")
                prep.setString(2, "%$term%")
                prep.executeQuery().use { set ->
                    val keys = mutableListOf<String>()
                    while (set.next()) {
                        keys.add(set.getString("ID"))
                    }
                    get(keys, page)
                }
            }
        }
    }

    fun star(id: String, starred: Boolean): Entry? = transaction {
        val updated = Entries.update({ Entries.id eq id }) {
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

    fun getEntryVersions(id: String): List<EntryVersion> = transaction {
        EntryVersions.slice(EntryVersions.id, EntryVersions.version, EntryVersions.dateUpdated)
            .select { EntryVersions.id eq id }
            .orderBy(EntryVersions.version, SortOrder.ASC)
            .map {
                EntryVersion(
                    id = it[EntryVersions.id],
                    version = it[EntryVersions.version],
                    dateUpdated = it[EntryVersions.dateUpdated]
                )
            }
    }
}
