package lynks.group

import lynks.common.IdBasedNewEntity
import lynks.util.RandomUtils
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
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

    protected fun getOrCreateFromPath(pathElements: List<String>): T {
        val existingGroup = bestMatchingGroup(pathElements)
        val remainingPath = pathElements.joinToString("/")
            .removePrefix(existingGroup?.path ?: "").trim('/')
        return if (existingGroup != null && remainingPath.isEmpty()) {
            existingGroup
        } else {
            add(toCreateModel(pathElements.joinToString("/")))
        }
    }

    // find the best matching group from a given path (forming a parent-child hierarchy)
    private fun bestMatchingGroup(path: List<String>): T? {
        val elements = path.toMutableList()
        for (i in 0 until elements.size) {
            val searchPath = elements.joinToString("/")
            val group = collection.groupByPath(searchPath)
            if(group != null) {
                return group
            }
            elements.removeLast()
        }
        return null
    }

    private fun getGroupChildren(id: String): MutableSet<T> = transaction {
        Groups.selectAll().where { (Groups.parentId eq id) and (Groups.type eq groupType) }
            .map { toModel(toGroupRow(it), getGroupChildren(it[Groups.id])) }.toMutableSet()
    }

    private fun queryGroup(id: String): T? = transaction {
        Groups.selectAll().where { Groups.id eq id and (Groups.type eq groupType) }
            .map { toModel(toGroupRow(it), getGroupChildren(it[Groups.id])) }.singleOrNull()
    }

    private fun queryAllGroups(): List<T> = transaction {
        val groups = Groups.selectAll().where { (Groups.type eq groupType) }
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

    fun getFromPath(path: String): T? = collection.groupByPath(path)?.copy()

    fun subtree(id: String): List<T> = collection.subtree(id).map { it.copy() }

    fun sequence() = collection.all().asSequence()

    open fun add(group: U): T = transaction {
        val newId = RandomUtils.generateUid()
        Groups.insert(toInsert(newId, group))
        val created = queryGroup(newId)
            ?: throw IllegalStateException("Group $newId not found after insert")
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
                    val existing = queryGroup(id)
                        ?: throw IllegalStateException("Group $id not found after update")
                    collection.update(existing, extractParentId(group))
                } else {
                    log.info("No rows modified when updating group id={}", id)
                    null
                }
            }
        }
    }

    fun delete(id: String): Boolean = transaction {
        // delete children first
        Groups.selectAll().where { Groups.parentId eq id and (Groups.type eq groupType) }.forEach { delete(it[Groups.id]) }
        // delete main group
        Groups.deleteWhere { Groups.id eq id }.also { collection.delete(id) } > 0
    }

    protected abstract fun toModel(row: GroupRow, children: MutableSet<T>): T

    protected abstract fun toCreateModel(name: String): @UnsafeVariance U

    protected abstract fun toInsert(gid: String, entity: U): Groups.(InsertStatement<*>) -> Unit

    protected abstract fun toUpdate(entity: U): Groups.(UpdateBuilder<*>) -> Unit

    protected abstract fun extractParentId(entity: U): String?

}
