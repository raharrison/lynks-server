package resource

import io.ktor.util.extension
import org.apache.commons.lang3.StringUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import util.FileUtils
import util.RandomUtils
import util.RowMapper.toResource
import java.nio.file.Files
import java.nio.file.Paths

class ResourceManager {

    fun getFilesFor(entryId: String): List<Resource> = transaction {
        Resources.select { Resources.entryId eq entryId }.map { toResource(it, ::constructPath) }
    }

    fun getFile(id: String): Resource? = transaction {
        Resources.select { Resources.id eq id }.map { toResource(it, ::constructPath) }.single()
    }

    fun saveTempFile(src: String, data: ByteArray, type: ResourceType): String {
        val path = constructTempPath(src, type, fileExtension(type))
        FileUtils.writeToFile(path, data)
        return path.toString()
    }

    fun moveTempFiles(entryId: String, src: String): Boolean {
        val tempPath = Paths.get(TEMP_PATH, FileUtils.createTempFileName(src))
        if(Files.exists(tempPath)) {
            val target = Paths.get(BASE_PATH, entryId)
            Files.move(tempPath, target)
            Files.list(target).forEach {
                val id = RandomUtils.generateUid()
                val size = Files.size(it)
                Files.move(it, constructPath(entryId, id, it.extension))
                val filename = FileUtils.removeExtension(it.fileName.toString())
                saveGeneratedResource(id, entryId, ResourceType.valueOf(filename.toUpperCase()), size)
            }
            return true
        }
        return false
    }

    private fun saveGeneratedResource(id: String, entryId: String, type: ResourceType, size: Long): Resource {
        val time = System.currentTimeMillis()
        val format = fileExtension(type)
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
            getFile(id)!!
        }
    }

    fun saveGeneratedResource(entryId: String, type: ResourceType, file: ByteArray) {
        val id = RandomUtils.generateUid()
        saveGeneratedResource(id, entryId, type, file.size.toLong())
        val path = constructPath(entryId, id, fileExtension(type))
        FileUtils.writeToFile(path, file)
    }

    private fun constructPath(entryId: String, id: String, extension: String) = Paths.get(BASE_PATH, entryId, "$id.$extension")

    private fun constructTempPath(name: String, type: ResourceType, extension: String) =
            Paths.get(TEMP_PATH, FileUtils.createTempFileName(name), "${type.toString().toLowerCase()}.$extension")

    private fun fileExtension(type: ResourceType) = when (type) {
        ResourceType.SCREENSHOT -> SCREENSHOT_FORMAT
        ResourceType.THUMBNAIL -> THUMBNAIL_FORMAT
        ResourceType.DOCUMENT -> DOCUMENT_FORMAT
        else -> StringUtils.EMPTY
    }

    companion object {
        const val BASE_PATH = "media"
        const val TEMP_PATH = "media/temp"
        const val SCREENSHOT_FORMAT = "png"
        const val THUMBNAIL_FORMAT = "jpg"
        const val DOCUMENT_FORMAT = "html"
    }
}
