package db

import common.*
import group.*
import group.Collection
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import util.RandomUtils
import util.combine

abstract class EntryRepository<T : Entry, in U : NewEntry>(private val tagService: TagService,
                                                           private val collectionService: CollectionService) {

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

    fun get(page: PageRequest = DefaultPageRequest): List<T> = transaction {
        createPagedQuery(page).map { toModel(it) }
    }

    fun get(ids: List<String>, page: PageRequest = DefaultPageRequest): List<T> = transaction {
        if(ids.isEmpty()) emptyList()
        else createPagedQuery(page).combine { Entries.id inList ids }
                .map { toModel(it) }
    }

    private fun createPagedQuery(page: PageRequest): Query {
        return if (page.tag == null) getBaseQuery() else {
            val tags = tagService.subtree(page.tag).map { it.id }
            getBaseQuery(Entries.innerJoin(EntryTags))
                    .combine { EntryTags.groupId.inList(tags) }
        }.apply {
            orderBy(Entries.dateUpdated, false)
            limit(page.limit, page.offset)
        }
    }

    open fun add(entry: U): T = transaction {
        val newId = RandomUtils.generateUid()
        Entries.insert(toInsert(newId, entry))
        for (tag in entry.tags) {
            EntryTags.insert {
                it[groupId] = tag
                it[entryId] = newId
            }
        }
        for(collection in entry.collections) {
            EntryCollections.insert {
                it[groupId] = collection
                it[entryId] = newId
            }
        }
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
                    updateCollectionsForEntry(entry.collections, id)
                    get(id)
                } else {
                    null
                }
            }
        }
    }

    fun update(entry: T, newVersion: Boolean=false): T? = transaction {
        val where = getBaseQuery().combine { Entries.id eq entry.id }.where!!
        val updated = Entries.update({ where }, body = {
            toUpdate(entry)(it)
            if(newVersion) {
                it[dateUpdated] = System.currentTimeMillis()
                with(SqlExpressionBuilder) {
                    it.update(Entries.version, Entries.version + 1)
                }
            }
        })
        if(updated > 0) {
            updateTagsForEntry(entry.tags.map { it.id }, entry.id)
            updateCollectionsForEntry(entry.collections.map { it.id }, entry.id)
            get(entry.id)
        } else {
            null
        }
    }

    open fun delete(id: String): Boolean = transaction {
        val entry = getBaseQuery().adjustSlice { this.slice(Entries.type) }
                .combine { Entries.id eq id }
                .singleOrNull()

        entry?.let {
            return@transaction Entries.deleteWhere { Entries.id eq id } > 0
        }
        false
    }

    private fun updateTagsForEntry(tags: List<String>, id: String) {
        val currentTags = getTagsForEntry(id).map { it.id }
        val newTags = tags.toSet()

        currentTags.filterNot { newTags.contains(it) }
                .forEach {
                    EntryTags.deleteWhere {
                        EntryTags.entryId eq id and (EntryTags.groupId eq it)
                    }
                }

        newTags.filterNot { currentTags.contains(it) }
                .forEach { tag ->
                    EntryTags.insert {
                        it[groupId] = tag
                        it[entryId] = id
                    }
                }
    }

    private fun updateCollectionsForEntry(collections: List<String>, id: String) {
        val currentCollections = getCollectionsForEntry(id).map { it.id }
        val newCollections = collections.toSet()

        currentCollections.filterNot { newCollections.contains(it) }
                .forEach {
                    EntryCollections.deleteWhere {
                        EntryCollections.entryId eq id and (EntryCollections.groupId eq it)
                    }
                }

        newCollections.filterNot { currentCollections.contains(it) }
                .forEach { col ->
                    EntryCollections.insert {
                        it[groupId] = col
                        it[entryId] = id
                    }
                }
    }

    // TODO: Make private
    protected fun getTagsForEntry(id: String): List<Tag> {
        val tags = EntryTags.slice(EntryTags.groupId)
                .select { EntryTags.entryId eq id }
                .map { it[EntryTags.groupId] }
        return tagService.getIn(tags)
    }

    protected fun getCollectionsForEntry(id: String): List<Collection> {
        val collections = EntryCollections.slice(EntryCollections.groupId)
                .select { EntryCollections.entryId eq id }
                .map { it[EntryCollections.groupId] }
        return collectionService.getIn(collections)
    }

    protected abstract fun getBaseQuery(base: ColumnSet = Entries, where: BaseEntries = Entries): Query

    protected abstract fun toInsert(eId: String, entry: U): BaseEntries.(InsertStatement<*>) -> Unit

    protected abstract fun toUpdate(entry: U): BaseEntries.(UpdateBuilder<*>) -> Unit

    protected abstract fun toUpdate(entry: T): BaseEntries.(UpdateBuilder<*>) -> Unit

    protected abstract fun toModel(row: ResultRow, table: BaseEntries = Entries): T

}