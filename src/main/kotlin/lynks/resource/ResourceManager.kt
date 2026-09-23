package lynks.resource

import lynks.common.EntryId
import lynks.common.ResourceId
import lynks.common.UserId
import lynks.common.newResourceId
import lynks.db.EntryOwnership
import lynks.db.afterCommit
import lynks.db.onRollback
import lynks.util.FileUtils
import lynks.util.loggerFor
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

class ResourceManager(
    private val fileStore: FileStore,
    private val repository: ResourceRepository
) {

    private val log = loggerFor<ResourceManager>()

    fun getResourcesFor(userId: UserId, entryId: EntryId): List<Resource> = repository.getResourcesFor(userId, entryId)

    fun getResource(userId: UserId, entryId: EntryId, id: ResourceId): Resource? =
        repository.getResource(userId, entryId, id)

    fun getResourceAsFile(userId: UserId, entryId: EntryId, id: ResourceId): Pair<Resource, File>? {
        val res = repository.getResource(userId, entryId, id) ?: return null
        return Pair(res, fileStore.getFile(res.entryId, res.id, res.extension))
    }

    fun saveTempFile(src: String, data: ByteArray, type: ResourceType, extension: String): String =
        fileStore.saveTempFile(src, data, type, extension)

    fun saveTempUpload(userId: UserId, data: ByteArray, extension: String): Path =
        fileStore.saveTempUpload(userId, data, extension)

    fun findTempUpload(userId: UserId, name: String): Path? = fileStore.findTempUpload(userId, name)

    fun tempUploadBaseDir(): Path = fileStore.tempUploadBaseDir()

    fun createTempFile(src: String, extension: String): TempFile =
        fileStore.createTempFile(src, extension)

    fun deleteTempFiles(src: String) = fileStore.deleteTempFiles(src)

    fun constructTempBasePath(name: String): Path = fileStore.constructTempBasePath(name)

    fun constructTempUrlFromPath(path: String): String = fileStore.constructTempUrlFromPath(path)

    // Moves each temp file under the entry, putting it back if the transaction rolls back
    fun attach(entryId: EntryId, pending: List<PendingResource>): List<Resource> = transaction {
        pending.map { res ->
            val name = res.tempPath.fileName.toString()
            val extension = FileUtils.getExtension(name)
            val (target, size) = fileStore.moveFile(res.tempPath, entryId, res.id, extension)
            onRollback { FileUtils.moveFile(target, res.tempPath) }
            log.info(
                "Attached {} resource from={} to={} entry={}",
                res.resourceType.name.lowercase(),
                res.tempPath,
                target,
                entryId
            )
            repository.createOrUpdateRecord(res.id, entryId, name, extension, res.resourceType, size)
        }
    }

    fun migrateGeneratedResources(entryId: EntryId, generatedResources: List<GeneratedResource>): List<Resource> {
        val (present, missing) = generatedResources
            .map { PendingResource(newResourceId(), it.resourceType, Path.of(it.targetPath)) }
            .partition { it.tempPath.exists() }
        missing.forEach { log.warn("Generated resource for entry={} at {} does not exist", entryId, it.tempPath) }
        return attach(entryId, present)
    }

    fun saveGeneratedResource(
        id: ResourceId = newResourceId(),
        entryId: EntryId,
        name: String,
        extension: String,
        type: ResourceType,
        size: Long
    ): Resource = repository.createOrUpdateRecord(id, entryId, name, extension, type, size)

    fun saveGeneratedResource(entryId: EntryId, name: String, type: ResourceType, file: ByteArray): Resource = transaction {
        val extension = FileUtils.getExtension(name)
        val id = newResourceId()
        fileStore.writeFile(entryId, id, extension, file)
        onRollback { fileStore.deleteFileWithCleanup(entryId, id, extension) }
        repository.createOrUpdateRecord(id, entryId, name, extension, type, file.size.toLong())
    }

    fun saveUploadedResource(userId: UserId, entryId: EntryId, name: String, input: InputStream): Resource? = transaction {
        if (!EntryOwnership.isOwner(userId, entryId)) return@transaction null
        val id = newResourceId()
        val extension = FileUtils.getExtension(name)
        log.info("Saving uploaded resource entry={}", entryId)
        val (_, size) = fileStore.writeFile(entryId, id, extension, input)
        onRollback { fileStore.deleteFileWithCleanup(entryId, id, extension) }
        repository.createOrUpdateRecord(id, entryId, name, extension, ResourceType.UPLOAD, size)
    }

    fun updateResource(userId: UserId, entryId: EntryId, resource: Resource): Resource? = transaction {
        val id = resource.id
        repository.getResource(userId, entryId, id)?.let { originalResource ->
            val resourceName = resource.name
            val format = FileUtils.getExtension(resourceName)
            val versions = repository.getResourceVersions(originalResource.parentId)
            val moves = versions.map { res ->
                val oldPath = fileStore.constructPath(originalResource.entryId, res.id, originalResource.extension)
                val newPath = fileStore.constructPath(originalResource.entryId, res.id, format)
                oldPath to newPath
            }

            val missingFiles = moves.map { it.first }.filterNot { Files.exists(it) }
            if (missingFiles.isNotEmpty()) {
                missingFiles.forEach { path ->
                    log.warn("Missing resource file during update entry={} path={}", originalResource.entryId, path)
                }
                throw IllegalStateException("Missing resource files for update")
            }

            moves.forEach { (oldPath, newPath) ->
                log.info("Moving resources after entry update from={} to={} entry={}", oldPath.toString(), newPath.toString(), originalResource.entryId)
            }
            fileStore.renameFiles(moves)
            onRollback { fileStore.renameFiles(moves.map { (from, to) -> to to from }) }

            repository.updateRecord(originalResource.parentId, resourceName, format)
            repository.getResource(id)
        }
    }

    fun delete(userId: UserId, entryId: EntryId, id: ResourceId): Boolean = transaction {
        if (repository.getResource(userId, entryId, id) == null) return@transaction false
        val res = repository.deleteRecord(id) ?: return@transaction false
        afterCommit {
            log.info("Deleting entry resource id={} entry={}", res.id, res.entryId)
            fileStore.deleteFile(res.entryId, res.id, res.extension)
        }
        true
    }

    // callers have already confined the entry to its owner
    fun deleteAll(entryId: EntryId): Boolean = transaction {
        repository.deleteAllRecords(entryId)
        afterCommit {
            log.info("Recursively deleting all entry resources entry={}", entryId)
            fileStore.deleteAllFiles(entryId)
        }
        true
    }

    internal fun constructPath(entryId: EntryId, id: ResourceId = newResourceId()): Path =
        fileStore.constructPath(entryId, id)
}
