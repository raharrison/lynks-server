package resource

import common.Environment
import io.ktor.util.extension
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import util.FileUtils
import util.RandomUtils
import util.RowMapper.toResource
import util.toUrlString
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ResourceManager {

    fun getResourcesFor(entryId: String): List<Resource> = transaction {
        Resources.select { Resources.entryId eq entryId }
            .orderBy(Resources.dateCreated)
            .map { toResource(it) }
    }

    fun getResource(id: String): Resource? = transaction {
        Resources.select { Resources.id eq id }.map { toResource(it) }.singleOrNull()
    }

    fun getResourceAsFile(id: String): Pair<Resource, File>? {
        val res = getResource(id)
        return res?.let {
            val path = constructPath(res.entryId, res.id, res.extension)
            return Pair(res, path.toFile())
        }
    }

    fun saveTempFile(src: String, data: ByteArray, type: ResourceType, extension: String): String {
        val path = constructTempPath(src, type, extension)
        FileUtils.writeToFile(path, data)
        return Paths.get(Environment.server.resourceTempPath).relativize(path).toUrlString()
    }

    fun moveTempFiles(entryId: String, src: String): Boolean {
        val tempPath = constructTempPath(src)
        if (Files.exists(tempPath)) {
            val target = Paths.get(Environment.server.resourceBasePath, entryId)
            Files.move(tempPath, target)
            Files.list(target).use { it ->
                it.forEach {
                    val id = RandomUtils.generateUid()
                    val size = Files.size(it)
                    val extension = it.extension
                    Files.move(it, constructPath(entryId, id, extension))
                    val filename = FileUtils.removeExtension(it.fileName.toString())
                    saveGeneratedResource(id, entryId, "$id.$extension", extension, ResourceType.valueOf(filename.toUpperCase()), size)
                }
            }
            return true
        }
        return false
    }

    fun saveGeneratedResource(id: String = RandomUtils.generateUid(), entryId: String, name: String, format: String, type: ResourceType, size: Long): Resource {
        val time = System.currentTimeMillis()
        return transaction {
            Resources.insert {
                it[Resources.id] = id
                it[Resources.entryId] = entryId
                it[fileName] = name
                it[extension] = format
                it[Resources.type] = type
                it[Resources.size] = size
                it[dateCreated] = time
                it[dateUpdated] = time
            }
            getResource(id)!!
        }
    }

    fun saveGeneratedResource(entryId: String, type: ResourceType, extension: String, file: ByteArray): Resource {
        val id = RandomUtils.generateUid()
        return saveGeneratedResource(id, entryId, "$id.$extension", extension, type, file.size.toLong()).also {
            val path = constructPath(entryId, id, extension)
            FileUtils.writeToFile(path, file)
        }
    }

    fun saveUploadedResource(entryId: String, name: String, input: InputStream): Resource {
        val id = RandomUtils.generateUid()
        val ext = FileUtils.getExtension(name)
        val path = constructPath(entryId, id, ext)
        val file = path.toFile().apply {
            parentFile.mkdirs()
            createNewFile()
        }
        input.use { its -> file.outputStream().buffered().use { its.copyTo(it) } }
        return saveGeneratedResource(id, entryId, name, ext, ResourceType.UPLOAD, file.length())
    }

    fun constructPath(entryId: String, id: String): Path = Paths.get(Environment.server.resourceBasePath, entryId, id)

    private fun constructPath(entryId: String, id: String, extension: String): Path {
        val resId = if (extension.isNotEmpty()) "$id.$extension" else id
        return constructPath(entryId, resId)
    }

    private fun constructTempPath(name: String, type: ResourceType, extension: String) =
        Paths.get(Environment.server.resourceTempPath, FileUtils.createTempFileName(name), "${type.toString().toLowerCase()}.$extension")

    private fun constructTempPath(name: String) = Paths.get(Environment.server.resourceTempPath, FileUtils.createTempFileName(name))

    fun updateResource(resource: Resource): Resource? {
        val id = resource.id
        return getResource(id)?.let { originalResource ->
            val resourceName = resource.name
            val format = FileUtils.getExtension(resourceName)
            transaction {
                Resources.update({ Resources.id eq id }) {
                    it[fileName] = resourceName
                    it[extension] = format
                    it[dateUpdated] = System.currentTimeMillis()
                }
                val updatedResource = getResource(id)!!
                val oldPath = constructPath(updatedResource.entryId, id, originalResource.extension)
                val newPath = constructPath(updatedResource.entryId, id, updatedResource.extension)
                Files.move(oldPath, newPath)
                updatedResource
            }
        }
    }

    fun delete(id: String): Boolean = transaction {
        val res = getResource(id)
        res?.let {
            Resources.deleteWhere { Resources.id eq id }
            val path = constructPath(res.entryId, res.id, res.extension).toFile()
            if (path.exists())
                return@transaction path.delete()
            return@transaction true
        }
        false
    }

    fun deleteAll(entryId: String): Boolean = transaction {
        Resources.deleteWhere { Resources.entryId eq entryId }
        val path = constructPath(entryId, "", "")
        path.toFile().let {
            if (it.exists()) it.deleteRecursively() else true
        }
    }
}
