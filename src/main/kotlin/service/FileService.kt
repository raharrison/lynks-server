package service

import model.File
import model.FileType
import model.Files
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import util.EMPTY_STRING
import util.FileUtils
import util.RandomUtils
import util.RowMapper.toFile

class FileService {

    fun getFilesFor(entryId: String): List<File> = transaction {
        Files.select { Files.entryId eq entryId }.map { toFile(it, ::constructPath) }
    }

    fun getFile(id: String): File? = transaction {
        Files.select { Files.id eq id }.map { toFile(it, ::constructPath) }.single()
    }

    fun saveTempFile(src: String, data: ByteArray, type: FileType): String {
        val path = constructTempPath(src, fileExtension(type))
        FileUtils.writeToFile(path, data)
        return path
    }

    fun saveGenerated(entryId: String, type: FileType, image: ByteArray): File {
        val id = RandomUtils.generateUid()
        val time = System.currentTimeMillis()
        val format = fileExtension(type)
        val path = constructPath(entryId, id, format)
        FileUtils.writeToFile(path, image)
        return transaction {
            Files.insert {
                it[Files.id] = id
                it[Files.entryId] = entryId
                it[Files.fileName] = id
                it[Files.extension] = format
                it[Files.type] = type
                it[Files.size] = image.size
                it[Files.dateCreated] = time
                it[Files.dateUpdated] = time
            }
            getFile(id)!!
        }
    }

    private fun constructPath(entryId: String, id: String, extension: String) = "$BASE_PATH/$entryId/$id.$extension"

    private fun constructTempPath(name: String, extension: String) = "$TEMP_PATH/${FileUtils.createTempFileName(name)}.$extension"

    private fun fileExtension(type: FileType) = when (type) {
        FileType.SCREENSHOT -> SCREENSHOT_FORMAT
        FileType.THUMBNAIL -> THUMBNAIL_FORMAT
        FileType.DOCUMENT -> DOCUMENT_FORMAT
        else -> EMPTY_STRING
    }

    companion object {
        const val BASE_PATH = "media"
        const val TEMP_PATH = "media/temp"
        const val SCREENSHOT_FORMAT = "png"
        const val THUMBNAIL_FORMAT = "jpg"
        const val DOCUMENT_FORMAT = "html"
    }
}