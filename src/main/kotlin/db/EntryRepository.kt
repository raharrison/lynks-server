package db

import common.Entries
import common.Entry
import common.NewEntry
import common.PageRequest
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import tag.EntryTags
import tag.Tag
import tag.TagService
import util.RandomUtils
import util.combine

abstract class EntryRepository<T : Entry, in U : NewEntry>(private val tagService: TagService) {

    fun get(id: String): T? = transaction {
        getBaseQuery().combine { Entries.id eq id }
                .mapNotNull { toModel(it) }
                .singleOrNull()
    }

    fun get(pageRequest: PageRequest): List<T> = transaction {
        val select = if (pageRequest.tag == null) getBaseQuery() else {
            val tags = tagService.subtree(pageRequest.tag).map { it.id }
            getBaseQuery(Entries.innerJoin(EntryTags))
                    .combine { EntryTags.tagId.inList(tags) }
        }

        select.orderBy(Entries.dateUpdated, false)
                .limit(pageRequest.limit, pageRequest.offset)
                .map { toModel(it) }
    }

    open fun add(entry: U): T = transaction {
        val newId = RandomUtils.generateUid()
        Entries.insert(toInsert(newId, entry))
        addTagsForEntry(entry.tags, newId)
        get(newId)!!
    }

    fun update(entry: U): T {
        val id = entry.id
        return if (id == null) {
            add(entry)
        } else {
            transaction {
                Entries.update({ Entries.id eq id }, body = toUpdate(entry))
                updateTagsForEntry(entry.tags, id)
                get(id)!!
            }
        }
    }

    fun update(entry: T): T = transaction {
        Entries.update({ Entries.id eq entry.id }, body = toUpdate(entry))
        entry
    }

    fun delete(id: String): Boolean = transaction {
        EntryTags.deleteWhere { EntryTags.entryId eq id }
        Entries.deleteWhere { Entries.id eq id } > 0
    }

    private fun addTagsForEntry(tags: List<String>, id: String) = transaction {
        for (tag in tags) {
            EntryTags.insert {
                it[tagId] = tag
                it[entryId] = id
            }
        }
    }

    private fun updateTagsForEntry(tags: List<String>, id: String) {
        val currentTags = getTagsForEntry(id).map { it.id }
        val newTags = tags.toSet()

        currentTags.filterNot { newTags.contains(it) }
                .forEach {
                    EntryTags.deleteWhere {
                        EntryTags.entryId eq id and (EntryTags.tagId eq it)
                    }
                }

        newTags.filterNot { currentTags.contains(it) }
                .forEach { tag ->
                    EntryTags.insert {
                        it[tagId] = tag
                        it[entryId] = id
                    }
                }
    }

    // TODO: Make private
    protected fun getTagsForEntry(id: String): List<Tag> {
        val tags = EntryTags.slice(EntryTags.tagId)
                .select { EntryTags.entryId eq id }
                .map { it[EntryTags.tagId] }
        return tagService.getTags(tags)
    }

    protected abstract fun getBaseQuery(base: ColumnSet = Entries): Query

    protected abstract fun toInsert(eId: String, entry: U): Entries.(InsertStatement<*>) -> Unit

    protected abstract fun toUpdate(entry: U): Entries.(UpdateBuilder<*>) -> Unit

    protected abstract fun toUpdate(entry: T): Entries.(UpdateBuilder<*>) -> Unit

    protected abstract fun toModel(row: ResultRow): T

}