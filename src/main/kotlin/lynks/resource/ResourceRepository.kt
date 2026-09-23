package lynks.resource

import lynks.common.*
import lynks.common.RowMapper.toResource
import lynks.util.loggerFor
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ResourceRepository {

    private val log = loggerFor<ResourceRepository>()

    private fun ownedResources() = Resources.innerJoin(ResourceVersions, { id }, { resourceId })
        .join(Entries, JoinType.INNER, Resources.entryId, Entries.id)

    fun getResourcesFor(userId: UserId, entryId: EntryId): List<Resource> = transaction {
        ownedResources()
            .select(Resources.columns + ResourceVersions.columns)
            .where { (Resources.entryId eq entryId.value) and (Entries.userId eq userId.value) }
            .orderBy(Resources.dateCreated)
            .map { toResource(it) }
    }

    fun getResource(userId: UserId, entryId: EntryId, id: ResourceId): Resource? = transaction {
        ownedResources()
            .select(Resources.columns + ResourceVersions.columns)
            .where {
                (ResourceVersions.id eq id.value) and (Resources.entryId eq entryId.value) and
                    (Entries.userId eq userId.value)
            }
            .map { toResource(it) }
            .singleOrNull()
    }

    fun getResource(id: ResourceId): Resource? = transaction {
        ResourceVersions.innerJoin(Resources, { resourceId }, { Resources.id })
            .selectAll().where { ResourceVersions.id eq id.value }.map { toResource(it) }.singleOrNull()
    }

    fun getResourceVersions(parentId: String): List<Resource> = transaction {
        Resources.innerJoin(ResourceVersions, { id }, { resourceId })
            .selectAll().where { Resources.id eq parentId }
            .orderBy(Resources.dateCreated)
            .map { toResource(it) }
    }

    fun currentVersion(entryId: EntryId, name: String): Pair<ResourceId, Int> = transaction {
        Resources.select(Resources.id, Resources.currentVersion)
            .where { (Resources.entryId eq entryId.value) and (Resources.fileName eq name) }
            .map { Pair(ResourceId(it[Resources.id]), it[Resources.currentVersion]) }
            .singleOrNull() ?: Pair(newResourceId(), 0)
    }

    fun createOrUpdateRecord(
        id: ResourceId,
        entryId: EntryId,
        name: String,
        extension: String,
        type: ResourceType,
        size: Long
    ): Resource {
        val currentVersion = currentVersion(entryId, name)
        val nextVersion = currentVersion.second + 1
        val time = OffsetDateTime.now(ZoneOffset.UTC)
        return transaction {
            if (nextVersion == 1) {
                Resources.insert {
                    it[Resources.id] = currentVersion.first.value
                    it[Resources.entryId] = entryId.value
                    it[Resources.currentVersion] = nextVersion
                    it[fileName] = name
                    it[Resources.extension] = extension
                    it[Resources.type] = type
                    it[dateCreated] = time
                    it[dateUpdated] = time
                }
            } else {
                Resources.update({ Resources.id eq currentVersion.first.value }) {
                    it[Resources.currentVersion] = nextVersion
                    it[dateUpdated] = time
                }
            }
            ResourceVersions.insert {
                it[ResourceVersions.id] = id.value
                it[resourceId] = currentVersion.first.value
                it[version] = nextVersion
                it[ResourceVersions.size] = size
                it[dateCreated] = time
            }
            getResource(id) ?: throw IllegalStateException("Resource ${id.value} not found after insert")
        }
    }

    fun updateRecord(parentId: String, name: String, extension: String) {
        transaction {
            Resources.update({ Resources.id eq parentId }) {
                it[fileName] = name
                it[Resources.extension] = extension
                it[dateUpdated] = OffsetDateTime.now(ZoneOffset.UTC)
            }
        }
    }

    fun deleteRecord(id: ResourceId): Resource? = transaction {
        val res = getResource(id)
        res?.let {
            ResourceVersions.deleteWhere { ResourceVersions.id eq id.value }
            val maxVersion: Int? = ResourceVersions.select(ResourceVersions.version.max())
                .where { ResourceVersions.resourceId eq res.parentId }
                .singleOrNull()?.let { it[ResourceVersions.version.max()] }
            if (maxVersion == null) {
                Resources.deleteWhere { Resources.id eq res.parentId }
            } else {
                Resources.update({ Resources.id eq res.parentId }) {
                    it[currentVersion] = maxVersion
                    it[dateUpdated] = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            res
        }
    }

    fun deleteAllRecords(entryId: EntryId) {
        transaction {
            val resourceIds = Resources.select(Resources.id)
                .where { Resources.entryId eq entryId.value }
                .map { it[Resources.id] }
            ResourceVersions.deleteWhere { resourceId.inList(resourceIds) }
            Resources.deleteWhere { Resources.entryId eq entryId.value }
        }
    }
}
