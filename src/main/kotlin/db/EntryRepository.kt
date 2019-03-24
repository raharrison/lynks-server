package db

import common.*
import common.exception.InvalidModelException
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
        var table: ColumnSet = Entries

        val tagTable = EntryGroups.alias("tags")
        val collectionTable = EntryGroups.alias("collections")
        if(page.tag != null) {
            table = table.innerJoin(tagTable, { Entries.id }, { tagTable[EntryGroups.entryId] } )
        }
        if(page.collection != null) {
            table = table.innerJoin(collectionTable, { Entries.id }, { collectionTable[EntryGroups.entryId] } )
        }

        var query = getBaseQuery(table)

        page.tag?.let {
            val tags = tagService.subtree(page.tag).map { it.id }
            query = query.combine { tagTable[EntryGroups.groupId].inList(tags) }
        }

        page.collection?.let {
            val collections = collectionService.subtree(page.collection).map { it.id }
            query = query.combine { collectionTable[EntryGroups.groupId].inList(collections) }
        }

        return query.apply {
            orderBy(Entries.dateUpdated, false)
            limit(page.limit, page.offset)
        }
    }

    open fun add(entry: U): T = transaction {
        val newId = RandomUtils.generateUid()
        validateTags(entry.tags)
        validateCollections(entry.collections)
        Entries.insert(toInsert(newId, entry))
        for (group in entry.tags + entry.collections) {
            EntryGroups.insert {
                it[groupId] = group
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
            validateTags(entry.tags)
            validateCollections(entry.collections)
            transaction {
                val where = getBaseQuery().combine { Entries.id eq id }.where!!
                val updated = Entries.update({ where }, body = {
                    toUpdate(entry)(it)
                    with(SqlExpressionBuilder) {
                        it.update(Entries.version, Entries.version + 1)
                    }
                })
                if(updated > 0) {
                    updateGroupsForEntry(entry.tags + entry.collections, id)
                    get(id)
                } else {
                    null
                }
            }
        }
    }

    fun update(entry: T, newVersion: Boolean=false): T? = transaction {
        validateTags(entry.tags.map { it.id })
        validateCollections(entry.collections.map { it.id })
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
            updateGroupsForEntry(entry.tags.map { it.id } + entry.collections.map { it.id }, entry.id)
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

    private fun updateGroupsForEntry(groups: List<String>, id: String) {
        val currentGroups = getGroupsForEntry(id).run { first.map { it.id } + second.map { it.id } }
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

    protected fun getGroupsForEntry(id: String): Pair<List<Tag>, List<Collection>> {
        val groupIds = EntryGroups.slice(EntryGroups.groupId)
            .select { EntryGroups.entryId eq id }
            .map { it[EntryGroups.groupId] }
        return Pair(tagService.getIn(groupIds), collectionService.getIn(groupIds))
    }

    private fun validateTags(ids: List<String>) {
        ids.forEach {
            if(tagService.get(it) == null)
                throw InvalidModelException("Unknown tag: $it")
        }
    }

    private fun validateCollections(ids: List<String>) {
        ids.forEach {
            if(collectionService.get(it) == null)
                throw InvalidModelException("Unknown collection: $it")
        }
    }

    protected abstract fun getBaseQuery(base: ColumnSet = Entries, where: BaseEntries = Entries): Query

    protected abstract fun toInsert(eId: String, entry: U): BaseEntries.(InsertStatement<*>) -> Unit

    protected abstract fun toUpdate(entry: U): BaseEntries.(UpdateBuilder<*>) -> Unit

    protected abstract fun toUpdate(entry: T): BaseEntries.(UpdateBuilder<*>) -> Unit

    protected abstract fun toModel(row: ResultRow, table: BaseEntries = Entries): T

}