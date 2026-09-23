package lynks.resource

import lynks.common.*
import lynks.util.FileUtils
import lynks.util.RandomUtils
import lynks.util.loggerFor
import lynks.util.toUrlString
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

class FileStore {

    private val log = loggerFor<FileStore>()

    internal fun constructPath(entryId: EntryId, id: ResourceId = newResourceId()): Path {
        val eid = entryId.value
        val firstDir = eid.substring(0, 1); val secondDir = eid.substring(0, 2)
        val path = Paths.get(Environment.resource.resourceBasePath, firstDir, secondDir, eid, id.value)
        val basePath = Paths.get(Environment.resource.resourceBasePath).toAbsolutePath().normalize()
        check(path.toAbsolutePath().normalize().startsWith(basePath)) {
            "Resolved resource path escapes base directory: $path"
        }
        return path
    }

    internal fun constructPath(entryId: EntryId, id: ResourceId, extension: String): Path {
        val resId = if (extension.isNotEmpty()) "$id.$extension" else id.value
        return constructPath(entryId, ResourceId(resId))
    }

    private fun constructTempBasePath(name: String, type: ResourceType, extension: String): Path {
        return Paths.get(Environment.resource.resourceTempPath, FileUtils.createTempFileName(name), "${type.toString().lowercase()}.$extension")
    }

    private fun constructTempBasePath(name: String, extension: String): Path {
        return Paths.get(Environment.resource.resourceTempPath, FileUtils.createTempFileName(name), "${RandomUtils.generateUid()}.$extension")
    }

    fun constructTempBasePath(name: String): Path = Paths.get(Environment.resource.resourceTempPath, FileUtils.createTempFileName(name))

    fun constructTempUrlFromPath(path: String): String {
        val resolvedPath = Path.of(path)
        return if (resolvedPath.isAbsolute) Paths.get(Environment.resource.resourceTempPath).toAbsolutePath().relativize(Path.of(path)).toUrlString()
        else Path.of(path).toUrlString()
    }

    fun saveTempFile(src: String, data: ByteArray, type: ResourceType, extension: String): String {
        val path = constructTempBasePath(src, type, extension)
        FileUtils.writeToFile(path, data)
        log.info("Temporary resource saved at {} src={} type={}", path.toString(), src, type)
        return path.toAbsolutePath().toUrlString()
    }

    fun tempUploadBaseDir(): Path =
        Paths.get(Environment.resource.resourceTempPath, TEMP_UPLOAD_DIR).toAbsolutePath().normalize()

    // Pasted images belong to no entry yet, so each user's are kept apart and only resolved for that user
    private fun tempUploadDir(userId: UserId): Path = tempUploadBaseDir().resolve(userId.value)

    fun saveTempUpload(userId: UserId, data: ByteArray, extension: String): Path {
        val path = tempUploadDir(userId).resolve("${RandomUtils.generateUid()}.$extension")
        FileUtils.writeToFile(path, data)
        log.info("Temporary upload saved at {}", path)
        return path
    }

    // The name comes from user markdown, so it must not resolve outside the upload directory
    fun findTempUpload(userId: UserId, name: String): Path? {
        val dir = tempUploadDir(userId)
        val path = runCatching { dir.resolve(name).normalize() }.getOrNull() ?: return null
        return path.takeIf { it.parent == dir && Files.isRegularFile(it) }
    }

    fun createTempFile(src: String, extension: String): TempFile {
        val path = constructTempBasePath(src, extension)
        log.info("Temp file created at {}", path.toUrlString())
        return TempFile(src, extension, path)
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

    fun writeFile(entryId: EntryId, id: ResourceId, extension: String, data: ByteArray) {
        val path = constructPath(entryId, id, extension)
        FileUtils.writeToFile(path, data)
    }

    fun writeFile(entryId: EntryId, id: ResourceId, extension: String, input: InputStream): Pair<File, Long> {
        val path = constructPath(entryId, id, extension)
        val file = path.toFile().apply {
            parentFile.mkdirs()
            createNewFile()
        }
        input.use { its -> file.outputStream().buffered().use { its.copyTo(it) } }
        return Pair(file, file.length())
    }

    fun moveFile(sourcePath: Path, entryId: EntryId, id: ResourceId, extension: String): Pair<Path, Long> {
        val target = constructPath(entryId, id, extension)
        val size = Files.size(sourcePath)
        FileUtils.moveFile(sourcePath, target)
        return Pair(target, size)
    }

    fun getFile(entryId: EntryId, id: ResourceId, extension: String): File {
        return constructPath(entryId, id, extension).toFile()
    }

    fun deleteFile(entryId: EntryId, id: ResourceId, extension: String): Boolean {
        val path = constructPath(entryId, id, extension).toFile()
        if (path.exists()) return path.delete()
        return true
    }

    fun deleteFileWithCleanup(entryId: EntryId, id: ResourceId, extension: String) {
        val path = constructPath(entryId, id, extension)
        FileUtils.deleteWithParentIfEmpty(path)
    }

    fun deleteFileWithCleanup(path: Path) {
        FileUtils.deleteWithParentIfEmpty(path)
    }

    fun deleteAllFiles(entryId: EntryId): Boolean {
        val path = constructPath(entryId, ResourceId(""), "")
        return path.toFile().let {
            if (it.exists()) it.deleteRecursively() else true
        }
    }

    fun renameFiles(moves: List<Pair<Path, Path>>) {
        try {
            moves.forEach { (oldPath, newPath) ->
                Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            moves.forEach { (oldPath, newPath) ->
                if (Files.exists(newPath) && !Files.exists(oldPath)) {
                    runCatching { Files.move(newPath, oldPath, StandardCopyOption.REPLACE_EXISTING) }
                }
            }
            throw e
        }
    }
}
