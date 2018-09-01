package group

import common.IdBasedNewEntity
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import util.RandomUtils

abstract class GroupService<T : Grouping<T>, in U : IdBasedNewEntity, E: Groups>(private val mainTable: E, private val joinTable: EntryGroups) {

    private val collection by lazy {
        GroupCollection<T>().apply { build(queryAllGroups()) }
    }

    protected fun getGroupChildren(id: String): MutableSet<T> = transaction {
        mainTable.select { mainTable.parentId eq id }.map { toModel(it) }.toMutableSet()
    }

    private fun queryGroup(id: String): T? = transaction {
        mainTable.select { mainTable.id eq id}
                .map { toModel(it) }.singleOrNull()
    }

    private fun queryAllGroups(): List<T> = transaction {
        mainTable.select { mainTable.parentId.isNull() }
                .map { toModel(it) }
    }

    fun rebuild() = collection.build(queryAllGroups())

    fun getAll(): List<T> = collection.rootGroups().map { it.copy() }

    fun getIn(ids: List<String>): List<T> = collection.groupsIn(ids).map { it.copy() }

    fun get(id: String): T? = collection.group(id)?.copy()

    fun subtree(id: String): List<T> = collection.subtree(id).map { it.copy() }

    fun add(group: U): T = transaction {
        val newId = RandomUtils.generateUid()
        mainTable.insert(toInsert(newId, group))
        val created =  queryGroup(newId)!!
        collection.add(created, extractParentId(group))
    }

    fun update(group: U): T? {
        val id = group.id
        return if (id == null) {
            add(group)
        } else {
            transaction {
                val updated = mainTable.update({ mainTable.id eq id }, body = toUpdate(group))
                if(updated > 0) {
                    collection.update(queryGroup(id)!!, extractParentId(group))
                } else null
            }
        }
    }

    fun delete(id: String): Boolean = transaction {
        joinTable.deleteWhere { joinTable.groupId eq id }
        // delete children
        mainTable.select { mainTable.parentId eq id }.forEach { delete(it[mainTable.id]) }
        mainTable.deleteWhere { mainTable.id eq id }.also { collection.delete(id) } > 0
    }

    protected abstract fun toModel(row: ResultRow): T

    protected abstract fun toInsert(eId: String, entity: U): E.(InsertStatement<*>) -> Unit

    protected abstract fun toUpdate(entity: U): E.(UpdateBuilder<*>) -> Unit

    protected abstract fun extractParentId(entity: U): String?

}