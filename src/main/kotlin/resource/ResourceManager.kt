package resource

import org.apache.commons.lang3.StringUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import util.FileUtils
import util.RandomUtils
import util.RowMapper.toResource
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

    fun saveGenerated(entryId: String, type: ResourceType, image: ByteArray): Resource {
        val id = RandomUtils.generateUid()
        val time = System.currentTimeMillis()
        val format = fileExtension(type)
        val path = constructPath(entryId, id, format)
        FileUtils.writeToFile(path, image)
        return transaction {
            Resources.insert {
                it[Resources.id] = id
                it[Resources.entryId] = entryId
                it[Resources.fileName] = id
                it[Resources.extension] = format
                it[Resources.type] = type
                it[Resources.size] = image.size
                it[Resources.dateCreated] = time
                it[Resources.dateUpdated] = time
            }
            getFile(id)!!
        }
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
