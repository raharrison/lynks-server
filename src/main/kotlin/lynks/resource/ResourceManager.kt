package lynks.resource

import lynks.common.Environment
import lynks.common.RowMapper.toResource
import lynks.util.FileUtils
import lynks.util.RandomUtils
import lynks.util.loggerFor
import lynks.util.toUrlString
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

class ResourceManager {

    private val log = loggerFor<ResourceManager>()

    fun getResourcesFor(entryId: String): List<Resource> = transaction {
        Resources.innerJoin(ResourceVersions, {id}, {resourceId})
            .select { Resources.entryId eq entryId }
            .orderBy(Resources.dateCreated)
            .map { toResource(it) }
    }

    private fun getResourceVersions(parentId: String): List<Resource> = transaction {
        Resources.innerJoin(ResourceVersions, {id}, {resourceId})
            .select { Resources.id eq parentId }
            .orderBy(Resources.dateCreated)
            .map { toResource(it) }
    }

    fun getResource(id: String): Resource? = transaction {
        ResourceVersions.innerJoin(Resources, { resourceId }, { Resources.id })
            .select { ResourceVersions.id eq id }.map { toResource(it) }.singleOrNull()
    }

    fun getResourceAsFile(id: String): Pair<Resource, File>? {
        val res = getResource(id)
        return res?.let {
            val path = constructPath(res.entryId, res.id, res.extension)
            return Pair(res, path.toFile())
        }
    }

    fun saveTempFile(src: String, data: ByteArray, type: ResourceType, extension: String): String {
        val path = constructTempBasePath(src, type, extension)
        FileUtils.writeToFile(path, data)
        log.info("Temporary resource saved at {} src={} type={}", path.toString(), src, type)
        return path.toAbsolutePath().toUrlString()
    }

    fun createTempFile(src: String, extension: String): TempFile {
        val path = constructTempBasePath(src, extension)
        log.info("Temp file created at {}", path.toUrlString())
        return TempFile(src, extension, path)
    }

    fun migrateGeneratedResources(entryId: String, generatedResources: List<GeneratedResource>): List<Resource> {
        val resources = mutableListOf<Resource>()
        log.info("Migrating {} temporary resources for entry={}", generatedResources.size, entryId)
        for (generatedResource in generatedResources) {
            val tempResourcePath = Path.of(generatedResource.targetPath)
            if (tempResourcePath.exists()) {
                val savedResource = saveGeneratedResource(entryId, generatedResource.resourceType, tempResourcePath)
                resources.add(savedResource)
            } else {
                log.warn("Generated resource for entry={} at {} does not exist", entryId, generatedResource.targetPath)
            }
        }
        return resources
    }

    fun deleteTempFiles(src: String) {
        val tempPath = constructTempBasePath(src)
        if (Files.exists(tempPath)) {
            FileUtils.deleteDirectories(listOf(tempPath))
            log.info("Temp files deleted for src={}", src)
        } else {
            log.debug("No temporary files to remove for src={}", src)
        }
    }

    // get resource grouping id and current max version based on entry id and name
    private fun currentVersion(entryId: String, name: String): Pair<String, Int> = transaction {
        Resources.slice(Resources.id, Resources.currentVersion)
            .select { (Resources.entryId eq entryId) and (Resources.fileName eq name) }
            .map { Pair(it[Resources.id], it[Resources.currentVersion]) }
            .singleOrNull() ?: Pair(RandomUtils.generateUid(), 0)
    }

    fun saveGeneratedResource(
        id: String = RandomUtils.generateUid(),
        entryId: String,
        name: String,
        extension: String,
        type: ResourceType,
        size: Long
    ): Resource {
        val currentVersion = currentVersion(entryId, name)
        val nextVersion = currentVersion.second + 1
        val time = System.currentTimeMillis()
        return transaction {
            if (nextVersion == 1) {
                // create new resource
                Resources.insert {
                    it[Resources.id] = currentVersion.first
                    it[Resources.entryId] = entryId
                    it[Resources.currentVersion] = nextVersion
                    it[fileName] = name
                    it[Resources.extension] = extension
                    it[Resources.type] = type
                    it[dateCreated] = time
                    it[dateUpdated] = time
                }
            } else {
                // new version of existing resource
                Resources.update({Resources.id eq currentVersion.first}) {
                    it[Resources.currentVersion] = nextVersion
                    it[dateUpdated] = time
                }
            }
            ResourceVersions.insert {
                it[ResourceVersions.id] = id
                it[resourceId] = currentVersion.first
                it[version] = nextVersion
                it[ResourceVersions.size] = size
                it[dateCreated] = time
            }
            getResource(id)!!
        }
    }

    fun saveGeneratedResource(entryId: String, name: String, type: ResourceType, file: ByteArray): Resource {
        val extension = FileUtils.getExtension(name)
        return saveGeneratedResource(
                entryId = entryId,
                name = name,
                extension = extension,
                type = type,
                size = file.size.toLong()).also {
            val path = constructPath(entryId, it.id, it.extension)
            log.info("Saving generated resource to {} entry={}", path.toString(), entryId)
            FileUtils.writeToFile(path, file)
        }
    }

    fun saveGeneratedResource(entryId: String, type: ResourceType, path: Path): Resource {
        val id = RandomUtils.generateUid()
        val name = path.fileName.toString()
        val extension = FileUtils.getExtension(name)
        val target = constructPath(entryId, id, extension)
        val size = Files.size(path)
        log.info("Moving {} resource from={} to={} entry={}", type.name.lowercase(), path.toString(), target.toString(), entryId)
        return saveGeneratedResource(id, entryId, name, extension, type, size).also {
            FileUtils.moveFile(path, target)
        }
    }

    fun saveUploadedResource(entryId: String, name: String, input: InputStream): Resource {
        val id = RandomUtils.generateUid()
        val ext = FileUtils.getExtension(name)
        val path = constructPath(entryId, id, ext)
        log.info("Saving uploaded resource to {} entry={}", path.toString(), entryId)
        val file = path.toFile().apply {
            parentFile.mkdirs()
            createNewFile()
        }
        input.use { its -> file.outputStream().buffered().use { its.copyTo(it) } }
        return saveGeneratedResource(id, entryId, name, ext, ResourceType.UPLOAD, file.length())
    }

    internal fun constructPath(entryId: String, id: String = RandomUtils.generateUid()): Path {
        val firstDir = entryId.substring(0, 1); val secondDir = entryId.substring(0, 2)
        return Paths.get(Environment.resource.resourceBasePath, firstDir, secondDir, entryId, id)
    }

    private fun constructPath(entryId: String, id: String, extension: String): Path {
        val resId = if (extension.isNotEmpty()) "$id.$extension" else id
        return constructPath(entryId, resId)
    }

    private fun constructTempBasePath(name: String, type: ResourceType, extension: String): Path {
        val date = System.currentTimeMillis()
        return Paths.get(Environment.resource.resourceTempPath, FileUtils.createTempFileName(name), "${type.toString().lowercase()}-$date.$extension")
    }

    private fun constructTempBasePath(name: String, extension: String): Path {
        return Paths.get(Environment.resource.resourceTempPath, FileUtils.createTempFileName(name), "${RandomUtils.generateUid()}.$extension")
    }

    fun constructTempUrlFromPath(path: String): String {
        val resolvedPath = Path.of(path)
        return if(resolvedPath.isAbsolute) Paths.get(Environment.resource.resourceTempPath).toAbsolutePath().relativize(Path.of(path)).toUrlString()
        else return Path.of(path).toUrlString()
    }

    fun constructTempBasePath(name: String): Path = Paths.get(Environment.resource.resourceTempPath, FileUtils.createTempFileName(name))

    fun updateResource(resource: Resource): Resource? {
        val id = resource.id
        return getResource(id)?.let { originalResource ->
            val resourceName = resource.name
            val format = FileUtils.getExtension(resourceName)
            transaction {
                Resources.update({ Resources.id eq originalResource.parentId }) {
                    it[fileName] = resourceName
                    it[extension] = format
                    it[dateUpdated] = System.currentTimeMillis()
                }
                val updatedResource = getResource(id)!!
                // move all versions of the resource to update file extensions
                getResourceVersions(updatedResource.parentId).forEach { res ->
                    val oldPath = constructPath(updatedResource.entryId, res.id, originalResource.extension)
                    val newPath = constructPath(updatedResource.entryId, res.id, updatedResource.extension)
                    log.info("Moving resources after entry update from={} to={} entry={}", oldPath.toString(), newPath.toString(), updatedResource.entryId)
                    Files.move(oldPath, newPath)
                }
                updatedResource
            }
        }
    }

    fun delete(id: String): Boolean = transaction {
        val res = getResource(id)
        res?.let {
            ResourceVersions.deleteWhere { ResourceVersions.id eq id }
            // find current max version (if any) after deletion
            val maxVersion = ResourceVersions.slice(ResourceVersions.version.max())
                .select { ResourceVersions.resourceId eq res.parentId }
                .firstOrNull()?.get(ResourceVersions.version.max())
            if (maxVersion == null) {
                // no versions left, remove the parent resource
                Resources.deleteWhere { Resources.id eq res.parentId }
            } else {
                // update the parent resource version
                Resources.update({ Resources.id eq res.parentId }) {
                    it[currentVersion] = maxVersion
                    it[dateUpdated] = System.currentTimeMillis()
                }
            }
            val path = constructPath(res.entryId, res.id, res.extension).toFile()
            log.info("Deleting entry resource at {} entry={}", path, res.entryId)
            if (path.exists())
                return@transaction path.delete()
            return@transaction true
        }
        false
    }

    fun deleteAll(entryId: String): Boolean = transaction {
        val resourceIds = Resources.slice(Resources.id)
            .select { Resources.entryId eq entryId }
            .map { it[Resources.id] }
        ResourceVersions.deleteWhere { resourceId.inList(resourceIds) }
        Resources.deleteWhere { Resources.entryId eq entryId }
        val path = constructPath(entryId, "", "")
        log.info("Recursively deleting all entry resources at {} entry={}", path.toString(), entryId)
        path.toFile().let {
            if (it.exists()) it.deleteRecursively() else true
        }
    }
}
