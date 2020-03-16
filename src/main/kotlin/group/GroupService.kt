package group

import common.IdBasedNewEntity
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import util.RandomUtils

abstract class GroupService<T : Grouping<T>, in U : IdBasedNewEntity>(private val groupType: GroupType) {

    private val log = LoggerFactory.getLogger(this::class.java)

    private val collection by lazy {
        log.info("Building group tree for {}s", groupType.name.toLowerCase())
        GroupCollection<T>().apply { build(queryAllGroups()) }
    }

    protected fun getGroupChildren(id: String): MutableSet<T> = transaction {
        Groups.select { (Groups.parentId eq id) and (Groups.type eq groupType) }.map { toModel(it) }.toMutableSet()
    }

    private fun queryGroup(id: String): T? = transaction {
        Groups.select { Groups.id eq id and (Groups.type eq groupType) }
                .map { toModel(it) }.singleOrNull()
    }

    private fun queryAllGroups(): List<T> = transaction {
        Groups.select { Groups.parentId.isNull() and (Groups.type eq groupType) }
                .map { toModel(it) }
    }

    fun rebuild() {
        log.info("Rebuilding group tree for {}s", groupType.name.toLowerCase())
        collection.build(queryAllGroups())
    }

    fun getAll(): List<T> = collection.rootGroups().map { it.copy() }

    fun getIn(ids: List<String>): List<T> = collection.groupsIn(ids).map { it.copy() }

    fun get(id: String): T? = collection.group(id)?.copy()

    fun subtree(id: String): List<T> = collection.subtree(id).map { it.copy() }

    fun add(group: U): T = transaction {
        val newId = RandomUtils.generateUid()
        Groups.insert(toInsert(newId, group))
        val created =  queryGroup(newId)!!
        collection.add(created, extractParentId(group))
    }

    fun update(group: U): T? {
        val id = group.id
        return if (id == null) {
            add(group)
        } else {
            transaction {
                val updated = Groups.update({ Groups.id eq id }, body = toUpdate(group))
                if(updated > 0) {
                    collection.update(queryGroup(id)!!, extractParentId(group))
                } else {
                    log.info("No rows modified when updating group id={}", id)
                    null
                }
            }
        }
    }

    fun delete(id: String): Boolean = transaction {
        // delete children first
        Groups.select { Groups.parentId eq id and (Groups.type eq groupType) }.forEach { delete(it[Groups.id]) }
        // delete main group
        Groups.deleteWhere { Groups.id eq id }.also { collection.delete(id) } > 0
    }

    protected abstract fun toModel(row: ResultRow): T

    protected abstract fun toInsert(eId: String, entity: U): Groups.(InsertStatement<*>) -> Unit

    protected abstract fun toUpdate(entity: U): Groups.(UpdateBuilder<*>) -> Unit

    protected abstract fun extractParentId(entity: U): String?

}