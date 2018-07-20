package entry

import common.*
import db.EntryRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import tag.TagService
import util.RowMapper

class EntryService(tagService: TagService) : EntryRepository<Entry, NewEntry>(tagService) {

    override fun toModel(row: ResultRow, table: BaseEntries): Entry {
        return when (row[table.type]) {
            EntryType.LINK -> RowMapper.toLink(table, row, ::getTagsForEntry)
            EntryType.NOTE -> RowMapper.toNote(table, row, ::getTagsForEntry)
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

    fun search(term: String, page: PageRequest=PageRequest()): List<Entry> = transaction {
        val conn = TransactionManager.current().connection
        conn.prepareStatement("SELECT * FROM FT_SEARCH_DATA(?, 0, 0)").use {
            it.setString(1, term)
            it.executeQuery().use {
                val keys = mutableListOf<String>()
                while (it.next()) {
                    val res = it.getArray("KEYS")
                    (res.array as Array<*>).forEach { keys.add(it.toString()) }
                }
                get(keys, page)
            }
        }
    }

    fun star(id: String, starred: Boolean): Entry? = transaction {
        Entries.update({Entries.id eq id}) {
            it[Entries.starred] = starred
            // TODO: don't create new version for update
            with(SqlExpressionBuilder) {
                it.update(Entries.version, Entries.version + 1)
            }
        }
        get(id)
    }
}