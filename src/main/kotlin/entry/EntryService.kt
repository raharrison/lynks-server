package entry

import common.Entries
import common.Entry
import common.EntryType
import common.NewEntry
import db.EntryRepository
import org.jetbrains.exposed.sql.ColumnSet
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import tag.TagService
import util.RowMapper

class EntryService(tagService: TagService) : EntryRepository<Entry, NewEntry>(tagService) {

    override fun toModel(row: ResultRow): Entry {
        return when (row[Entries.type]) {
            EntryType.LINK -> RowMapper.toLink(row, ::getTagsForEntry)
            EntryType.NOTE -> RowMapper.toNote(row, ::getTagsForEntry)
        }
    }

    override fun getBaseQuery(base: ColumnSet): Query {
        return base.selectAll()
    }

    override fun toInsert(eId: String, entry: NewEntry): Entries.(UpdateBuilder<*>) -> Unit {
        throw NotImplementedError()
    }

    override fun toUpdate(entry: NewEntry): Entries.(UpdateBuilder<*>) -> Unit {
        throw NotImplementedError()
    }

    override fun toUpdate(entry: Entry): Entries.(UpdateBuilder<*>) -> Unit {
        throw NotImplementedError()
    }

    fun search(term: String): List<Entry> = transaction {
        val conn = TransactionManager.current().connection
        val st = conn.prepareStatement("SELECT * FROM FT_SEARCH_DATA(?, 0, 0)")
        st.setString(1, term)
        val rs = st.executeQuery()
        val keys = mutableListOf<String>()
        while(rs.next()) {
            val res = rs.getArray("KEYS")
            (res.array as Array<*>).forEach { keys.add(it.toString())}
        }
        keys.mapNotNull { get(it) }
    }
}