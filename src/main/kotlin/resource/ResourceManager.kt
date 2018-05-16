package resource

import io.ktor.util.extension
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
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
        Resources.select { Resources.entryId eq entryId }.map { toResource(it) }
    }

    fun getResource(id: String): Resource? = transaction {
        Resources.select { Resources.id eq id }.map { toResource(it) }.singleOrNull()
    }

    fun getResourceAsFile(id: String): File? {
        val res = getResource(id)
        return res?.let {
            val path = constructPath(res.entryId, res.name)
            return path.toFile()
        }
    }

    fun saveTempFile(src: String, data: ByteArray, type: ResourceType, extension: String): String {
        val path = constructTempPath(src, type, extension)
        FileUtils.writeToFile(path, data)
        return path.toUrlString()
    }

    fun moveTempFiles(entryId: String, src: String): Boolean {
        val tempPath = Paths.get(TEMP_PATH, FileUtils.createTempFileName(src))
        if(Files.exists(tempPath)) {
            val target = Paths.get(BASE_PATH, entryId)
            Files.move(tempPath, target)
            Files.list(target).forEach {
                val id = RandomUtils.generateUid()
                val size = Files.size(it)
                val extension = it.extension
                Files.move(it, constructPath(entryId, id, extension))
                val filename = FileUtils.removeExtension(it.fileName.toString())
                saveGeneratedResource(id, entryId, "$id.$extension", extension, ResourceType.valueOf(filename.toUpperCase()), size)
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
                it[Resources.fileName] = name
                it[Resources.extension] = format
                it[Resources.type] = type
                it[Resources.size] = size
                it[Resources.dateCreated] = time
                it[Resources.dateUpdated] = time
            }
            getResource(id)!!
        }
    }

    fun saveGeneratedResource(entryId: String, type: ResourceType, extension: String, file: ByteArray) {
        val id = RandomUtils.generateUid()
        saveGeneratedResource(id, entryId, "$id.$extension", extension, type, file.size.toLong())
        val path = constructPath(entryId, id, extension)
        FileUtils.writeToFile(path, file)
    }

    fun saveUploadedResource(entryId: String, name: String, input: InputStream): Resource {
        val id = RandomUtils.generateUid()
        val path = constructPath(entryId, name)
        val ext = FileUtils.getExtension(name)
        val file = path.toFile().apply {
            parentFile.mkdirs()
            createNewFile()
        }
        input.use { its -> file.outputStream().buffered().use { its.copyTo(it) } }
        return saveGeneratedResource(id, entryId, name, ext, ResourceType.UPLOAD, file.length())
    }

    private fun constructPath(entryId: String, id: String, extension: String) = Paths.get(BASE_PATH, entryId, "$id.$extension")

    fun constructPath(entryId: String, name: String): Path = Paths.get(BASE_PATH, entryId, name)

    private fun constructTempPath(name: String, type: ResourceType, extension: String) =
            Paths.get(TEMP_PATH, FileUtils.createTempFileName(name), "${type.toString().toLowerCase()}.$extension")

    fun delete(id: String): Boolean = transaction {
        val res = getResource(id)
        res?.let {
            Resources.deleteWhere { Resources.id eq id }
            val path = constructPath(res.entryId, res.name)
            return@transaction path.toFile().delete()
        }
        false
    }

    fun deleteAll(entryId: String): Boolean = transaction {
        Resources.deleteWhere { Resources.entryId eq entryId }
        val path = constructPath(entryId, "")
        path.toFile().deleteRecursively()
    }

    companion object {
        const val BASE_PATH = "media"
        const val TEMP_PATH = "media/temp"
    }
}
