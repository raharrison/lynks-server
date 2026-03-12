package lynks.resource

import lynks.common.EntryId
import lynks.common.ResourceId
import lynks.common.newResourceId
import lynks.util.FileUtils
import lynks.util.loggerFor
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists

class ResourceManager(
    private val fileStore: FileStore,
    private val repository: ResourceRepository
) {

    private val log = loggerFor<ResourceManager>()

    fun getResourcesFor(entryId: EntryId): List<Resource> = repository.getResourcesFor(entryId)

    fun getResource(id: ResourceId): Resource? = repository.getResource(id)

    fun getResourceAsFile(id: ResourceId): Pair<Resource, File>? {
        val res = repository.getResource(id) ?: return null
        return Pair(res, fileStore.getFile(res.entryId, res.id, res.extension))
    }

    fun saveTempFile(src: String, data: ByteArray, type: ResourceType, extension: String): String =
        fileStore.saveTempFile(src, data, type, extension)

    fun createTempFile(src: String, extension: String): TempFile =
        fileStore.createTempFile(src, extension)

    fun deleteTempFiles(src: String) = fileStore.deleteTempFiles(src)

    fun constructTempBasePath(name: String): Path = fileStore.constructTempBasePath(name)

    fun constructTempUrlFromPath(path: String): String = fileStore.constructTempUrlFromPath(path)

    fun migrateGeneratedResources(entryId: EntryId, generatedResources: List<GeneratedResource>): List<Resource> {
        log.info("Migrating {} temporary resources for entry={}", generatedResources.size, entryId)
        val missing = generatedResources
            .map { Path.of(it.targetPath) }
            .filterNot { it.exists() }
        if (missing.isNotEmpty()) {
            missing.forEach { path ->
                log.warn("Generated resource for entry={} at {} does not exist", entryId, path)
            }
            return emptyList()
        }

        val resources = mutableListOf<Resource>()
        try {
            for (generatedResource in generatedResources) {
                val tempResourcePath = Path.of(generatedResource.targetPath)
                val savedResource = saveGeneratedResource(entryId, generatedResource.resourceType, tempResourcePath)
                resources.add(savedResource)
            }
            return resources
        } catch (e: Exception) {
            resources.forEach { res ->
                runCatching { delete(res.id) }
            }
            throw e
        }
    }

    fun saveGeneratedResource(
        id: ResourceId = newResourceId(),
        entryId: EntryId,
        name: String,
        extension: String,
        type: ResourceType,
        size: Long
    ): Resource = repository.createOrUpdateRecord(id, entryId, name, extension, type, size)

    fun saveGeneratedResource(entryId: EntryId, name: String, type: ResourceType, file: ByteArray): Resource {
        val extension = FileUtils.getExtension(name)
        val id = newResourceId()
        val path = fileStore.constructPath(entryId, id, extension)
        log.info("Saving generated resource to {} entry={}", path.toString(), entryId)
        fileStore.writeFile(entryId, id, extension, file)
        return try {
            repository.createOrUpdateRecord(
                id = id,
                entryId = entryId,
                name = name,
                extension = extension,
                type = type,
                size = file.size.toLong()
            )
        } catch (e: Exception) {
            fileStore.deleteFileWithCleanup(entryId, id, extension)
            throw e
        }
    }

    fun saveGeneratedResource(entryId: EntryId, type: ResourceType, path: Path): Resource {
        val id = newResourceId()
        val name = path.fileName.toString()
        val extension = FileUtils.getExtension(name)
        val (target, size) = fileStore.moveFile(path, entryId, id, extension)
        log.info("Moving {} resource from={} to={} entry={}", type.name.lowercase(), path.toString(), target.toString(), entryId)
        return try {
            repository.createOrUpdateRecord(id, entryId, name, extension, type, size)
        } catch (e: Exception) {
            if (Files.exists(target) && !Files.exists(path)) {
                runCatching { Files.move(target, path, StandardCopyOption.REPLACE_EXISTING) }
            } else {
                fileStore.deleteFileWithCleanup(target)
            }
            throw e
        }
    }

    fun saveUploadedResource(entryId: EntryId, name: String, input: InputStream): Resource {
        val id = newResourceId()
        val ext = FileUtils.getExtension(name)
        log.info("Saving uploaded resource entry={}", entryId)
        val (file, size) = fileStore.writeFile(entryId, id, ext, input)
        return try {
            repository.createOrUpdateRecord(id, entryId, name, ext, ResourceType.UPLOAD, size)
        } catch (e: Exception) {
            fileStore.deleteFileWithCleanup(entryId, id, ext)
            throw e
        }
    }

    fun updateResource(resource: Resource): Resource? {
        val id = resource.id
        return repository.getResource(id)?.let { originalResource ->
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

            try {
                repository.updateRecord(originalResource.parentId, resourceName, format)
                repository.getResource(id)
            } catch (e: Exception) {
                runCatching { fileStore.renameFiles(moves.map { (from, to) -> to to from }) }
                throw e
            }
        }
    }

    fun delete(id: ResourceId): Boolean {
        val res = repository.deleteRecord(id) ?: return false
        log.info("Deleting entry resource at {} entry={}", fileStore.constructPath(res.entryId, res.id, res.extension), res.entryId)
        return fileStore.deleteFile(res.entryId, res.id, res.extension)
    }

    fun deleteAll(entryId: EntryId): Boolean {
        repository.deleteAllRecords(entryId)
        val path = fileStore.constructPath(entryId, ResourceId(""), "")
        log.info("Recursively deleting all entry resources at {} entry={}", path.toString(), entryId)
        return fileStore.deleteAllFiles(entryId)
    }

    internal fun constructPath(entryId: EntryId, id: ResourceId = newResourceId()): Path =
        fileStore.constructPath(entryId, id)
}
