package lynks.group

import lynks.common.IdBasedNewEntity
import lynks.util.RandomUtils
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

abstract class GroupService<T : Grouping<T>, in U : IdBasedNewEntity>(private val groupType: GroupType) {

    private val log = LoggerFactory.getLogger(this::class.java)

    private val collection by lazy {
        log.info("Building group tree for {}s", groupType.name.lowercase())
        GroupCollection<T>().apply { build(queryAllGroups()) }
    }

    protected data class GroupRow(
        val id: String,
        val name: String,
        val parentId: String?,
        val dateCreated: Long,
        val dateUpdated: Long
    )

    protected fun resolveParentFromPath(path: String): T? = collection.groupByPath(path)

    private fun getGroupChildren(id: String): MutableSet<T> = transaction {
        Groups.select { (Groups.parentId eq id) and (Groups.type eq groupType) }
            .map { toModel(toGroupRow(it), getGroupChildren(it[Groups.id])) }.toMutableSet()
    }

    private fun queryGroup(id: String): T? = transaction {
        Groups.select { Groups.id eq id and (Groups.type eq groupType) }
            .map { toModel(toGroupRow(it), getGroupChildren(it[Groups.id])) }.singleOrNull()
    }

    private fun queryAllGroups(): List<T> = transaction {
        val groups = Groups.select { (Groups.type eq groupType) }
            .map { toGroupRow(it) }
        val groupsByParent = groups.groupBy { it.parentId }
        groupsByParent[null]?.map { row ->
            toModel(row, findGroupChildren(groupsByParent, row.id))
        } ?: emptyList()
    }

    private fun findGroupChildren(groupsByParent: Map<String?, List<GroupRow>>, parentId: String?): MutableSet<T> {
        return groupsByParent[parentId]
            ?.map { toModel(it, findGroupChildren(groupsByParent, it.id)) }
            ?.toMutableSet() ?: mutableSetOf()
    }

    private fun toGroupRow(row: ResultRow) = GroupRow(
        id = row[Groups.id],
        name = row[Groups.name],
        parentId = row[Groups.parentId],
        dateCreated = row[Groups.dateCreated],
        dateUpdated = row[Groups.dateUpdated]
    )

    fun rebuild() {
        log.info("Rebuilding group tree for {}s", groupType.name.lowercase())
        collection.build(queryAllGroups())
    }

    fun getAll(): List<T> = collection.rootGroups().map { it.copy() }

    fun getIn(ids: List<String>): List<T> = collection.groupsIn(ids).map { it.copy() }

    fun get(id: String): T? = collection.group(id)?.copy()

    fun subtree(id: String): List<T> = collection.subtree(id).map { it.copy() }

    fun sequence() = collection.all().asSequence()

    open fun add(group: U): T = transaction {
        val newId = RandomUtils.generateUid()
        Groups.insert(toInsert(newId, group))
        val created = queryGroup(newId)!!
        collection.add(created, extractParentId(group))
    }

    fun update(group: U): T? {
        val id = group.id
        return if (id == null) {
            add(group)
        } else {
            transaction {
                val updated = Groups.update({ Groups.id eq id }, body = toUpdate(group))
                if (updated > 0) {
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

    protected abstract fun toModel(row: GroupRow, children: MutableSet<T>): T

    protected abstract fun toInsert(eId: String, entity: U): Groups.(InsertStatement<*>) -> Unit

    protected abstract fun toUpdate(entity: U): Groups.(UpdateBuilder<*>) -> Unit

    protected abstract fun extractParentId(entity: U): String?

}
