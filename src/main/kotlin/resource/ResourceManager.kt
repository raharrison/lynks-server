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
import java.nio.file.Files
import java.nio.file.Paths

class ResourceManager {

    fun getResourcesFor(entryId: String): List<Resource> = transaction {
        Resources.select { Resources.entryId eq entryId }.map { toResource(it, ::constructPath) }
    }

    fun getResource(id: String): Resource? = transaction {
        Resources.select { Resources.id eq id }.map { toResource(it, ::constructPath) }.single()
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
                saveGeneratedResource(id, entryId, ResourceType.valueOf(filename.toUpperCase()), size, extension)
            }
            return true
        }
        return false
    }

    private fun saveGeneratedResource(id: String, entryId: String, type: ResourceType, size: Long, format: String): Resource {
        val time = System.currentTimeMillis()
        return transaction {
            Resources.insert {
                it[Resources.id] = id
                it[Resources.entryId] = entryId
                it[Resources.fileName] = id
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
        saveGeneratedResource(id, entryId, type, file.size.toLong(), extension)
        val path = constructPath(entryId, id, extension)
        FileUtils.writeToFile(path, file)
    }

    private fun constructPath(entryId: String, id: String, extension: String) = Paths.get(BASE_PATH, entryId, "$id.$extension")

    private fun constructTempPath(name: String, type: ResourceType, extension: String) =
            Paths.get(TEMP_PATH, FileUtils.createTempFileName(name), "${type.toString().toLowerCase()}.$extension")

    fun delete(id: String): Boolean = transaction {
        val res = getResource(id)
        res?.let {
            Resources.deleteWhere { Resources.id eq id }
            val path = constructPath(res.entryId, res.id, res.extension).parent
            path.toFile().deleteRecursively()
            return@transaction true
        }
        false
    }

    companion object {
        const val BASE_PATH = "media"
        const val TEMP_PATH = "media/temp"
    }
}
