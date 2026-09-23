package lynks.group

import lynks.common.IdBasedNewEntity
import lynks.common.UserId
import lynks.common.exception.InvalidModelException
import lynks.util.RandomUtils
import org.jetbrains.exposed.v1.core.Op
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
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

abstract class GroupService<T : Grouping<T>, in U : IdBasedNewEntity>(private val groupType: GroupType) {

    private val log = LoggerFactory.getLogger(this::class.java)

    // each user's tree is built on first use, since groups are only ever read within one user
    private val collections = ConcurrentHashMap<UserId, GroupCollection<T>>()

    private fun collection(userId: UserId): GroupCollection<T> = collections.computeIfAbsent(userId) {
        log.info("Building group tree for {}s user={}", groupType.name.lowercase(), userId)
        GroupCollection<T>().apply { build(queryAllGroups(userId)) }
    }

    protected data class GroupRow(
        val id: String,
        val name: String,
        val parentId: String?,
        val dateCreated: Instant,
        val dateUpdated: Instant
    )

    protected fun getOrCreateFromPath(userId: UserId, pathElements: List<String>): T {
        val existingGroup = bestMatchingGroup(userId, pathElements)
        val remainingPath = pathElements.joinToString("/")
            .removePrefix(existingGroup?.path ?: "").trim('/')
        return if (existingGroup != null && remainingPath.isEmpty()) {
            existingGroup
        } else {
            add(userId, toCreateModel(pathElements.joinToString("/")))
        }
    }

    // find the best matching group from a given path (forming a parent-child hierarchy)
    private fun bestMatchingGroup(userId: UserId, path: List<String>): T? {
        val elements = path.toMutableList()
        for (i in 0 until elements.size) {
            val searchPath = elements.joinToString("/")
            val group = collection(userId).groupByPath(searchPath)
            if(group != null) {
                return group
            }
            elements.removeLast()
        }
        return null
    }

    private fun owned(userId: UserId): Op<Boolean> = (Groups.userId eq userId.value) and (Groups.type eq groupType)

    private fun getGroupChildren(userId: UserId, id: String): MutableSet<T> = transaction {
        Groups.selectAll().where { (Groups.parentId eq id) and owned(userId) }
            .map { toModel(toGroupRow(it), getGroupChildren(userId, it[Groups.id])) }.toMutableSet()
    }

    private fun queryGroup(userId: UserId, id: String): T? = transaction {
        Groups.selectAll().where { (Groups.id eq id) and owned(userId) }
            .map { toModel(toGroupRow(it), getGroupChildren(userId, it[Groups.id])) }.singleOrNull()
    }

    private fun queryAllGroups(userId: UserId): List<T> = transaction {
        val groups = Groups.selectAll().where { owned(userId) }
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
        dateCreated = row[Groups.dateCreated].toInstant(),
        dateUpdated = row[Groups.dateUpdated].toInstant()
    )

    fun rebuild(userId: UserId) {
        log.info("Rebuilding group tree for {}s user={}", groupType.name.lowercase(), userId)
        collection(userId).build(queryAllGroups(userId))
    }

    fun getAll(userId: UserId): List<T> = collection(userId).rootGroups().map { it.copy() }

    fun getIn(userId: UserId, ids: List<String>): List<T> = collection(userId).groupsIn(ids).map { it.copy() }

    fun get(userId: UserId, id: String): T? = collection(userId).group(id)?.copy()

    fun getFromPath(userId: UserId, path: String): T? = collection(userId).groupByPath(path)?.copy()

    fun subtree(userId: UserId, id: String): List<T> = collection(userId).subtree(id).map { it.copy() }

    fun sequence(userId: UserId) = collection(userId).all().asSequence()

    open fun add(userId: UserId, group: U): T = transaction {
        val parentId = extractParentId(group)
        checkParent(userId, parentId)
        val newId = RandomUtils.generateUid()
        val insert = toInsert(newId, group)
        Groups.insert {
            insert(it)
            it[Groups.userId] = userId.value
        }
        val created = queryGroup(userId, newId)
            ?: throw IllegalStateException("Group $newId not found after insert")
        collection(userId).add(created, parentId)
    }

    fun update(userId: UserId, group: U): T? {
        val id = group.id
        return if (id == null) {
            add(userId, group)
        } else {
            transaction {
                val parentId = extractParentId(group)
                checkParent(userId, parentId)
                val updated = Groups.update({ (Groups.id eq id) and owned(userId) }, body = toUpdate(group))
                if (updated > 0) {
                    val existing = queryGroup(userId, id)
                        ?: throw IllegalStateException("Group $id not found after update")
                    collection(userId).update(existing, parentId)
                } else {
                    log.info("No rows modified when updating group id={}", id)
                    null
                }
            }
        }
    }

    fun delete(userId: UserId, id: String): Boolean = transaction {
        // delete children first
        Groups.selectAll().where { (Groups.parentId eq id) and owned(userId) }.forEach { delete(userId, it[Groups.id]) }
        // delete main group
        (Groups.deleteWhere { (Groups.id eq id) and owned(userId) } > 0).also { deleted ->
            if (deleted) collection(userId).delete(id)
        }
    }

    // a parent from another user or group type would otherwise only fail on the foreign key, or not at all
    private fun checkParent(userId: UserId, parentId: String?) {
        if (parentId != null && collection(userId).group(parentId) == null) {
            throw InvalidModelException("Unknown parent: $parentId")
        }
    }

    protected abstract fun toModel(row: GroupRow, children: MutableSet<T>): T

    protected abstract fun toCreateModel(name: String): @UnsafeVariance U

    protected abstract fun toInsert(gid: String, entity: U): Groups.(InsertStatement<*>) -> Unit

    protected abstract fun toUpdate(entity: U): Groups.(UpdateBuilder<*>) -> Unit

    protected abstract fun extractParentId(entity: U): String?

}
