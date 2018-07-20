package db

import common.*
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

    fun get(id: String, version: Int): T? = transaction {
        getBaseQuery(EntryVersions, EntryVersions).combine { EntryVersions.id eq id and
                (EntryVersions.version eq version) }
                .mapNotNull { toModel(it, EntryVersions) }
                .singleOrNull()
    }

    fun get(page: PageRequest): List<T> = transaction {
        createPagedQuery(page).map { toModel(it) }
    }

    fun get(ids: List<String>, page: PageRequest): List<T> = transaction {
        if(ids.isEmpty()) emptyList()
        else createPagedQuery(page).combine { Entries.id inList ids }
                .map { toModel(it) }
    }

    private fun createPagedQuery(page: PageRequest): Query {
        return if (page.tag == null) getBaseQuery() else {
            val tags = tagService.subtree(page.tag).map { it.id }
            getBaseQuery(Entries.innerJoin(EntryTags))
                    .combine { EntryTags.tagId.inList(tags) }
        }.apply {
            orderBy(Entries.dateUpdated, false)
            limit(page.limit, page.offset)
        }
    }

    open fun add(entry: U): T = transaction {
        val newId = RandomUtils.generateUid()
        Entries.insert(toInsert(newId, entry))
        addTagsForEntry(entry.tags, newId)
        get(newId)!!
    }

    fun update(entry: U): T? {
        val id = entry.id
        return if (id == null) {
            add(entry)
        } else {
            transaction {
                val where = getBaseQuery().combine { Entries.id eq id }.where!!
                val updated = Entries.update({ where }, body = {
                    toUpdate(entry)(it)
                    with(SqlExpressionBuilder) {
                        it.update(Entries.version, Entries.version + 1)
                    }
                })
                if(updated > 0) {
                    updateTagsForEntry(entry.tags, id)
                    get(id)
                } else {
                    null
                }
            }
        }
    }

    fun update(entry: T, newVersion: Boolean=false): T? = transaction {
        val where = getBaseQuery().combine { Entries.id eq entry.id }.where!!
        Entries.update({ where }, body = {
            toUpdate(entry)(it)
            if(newVersion) {
                it[dateUpdated] = System.currentTimeMillis()
                with(SqlExpressionBuilder) {
                    it.update(Entries.version, Entries.version + 1)
                }
            }
        })
        get(entry.id)
    }

    open fun delete(id: String): Boolean = transaction {
        val entry = getBaseQuery().adjustSlice { this.slice(Entries.type) }
                .combine { Entries.id eq id }
                .singleOrNull()

        entry?.let {
            EntryTags.deleteWhere { EntryTags.entryId eq id }
            return@transaction Entries.deleteWhere { Entries.id eq id } > 0
        }
        false
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

    protected abstract fun getBaseQuery(base: ColumnSet = Entries, where: BaseEntries = Entries): Query

    protected abstract fun toInsert(eId: String, entry: U): BaseEntries.(InsertStatement<*>) -> Unit

    protected abstract fun toUpdate(entry: U): BaseEntries.(UpdateBuilder<*>) -> Unit

    protected abstract fun toUpdate(entry: T): BaseEntries.(UpdateBuilder<*>) -> Unit

    protected abstract fun toModel(row: ResultRow, table: BaseEntries = Entries): T

}