package common

import org.jetbrains.exposed.sql.Table

object Files : Table() {
    val id = varchar("id", 12).primaryKey()
    val entryId = (varchar("entryId", 12) references Entries.id).index()
    val fileName = varchar("filename", 255)
    val extension = varchar("extension", 4)
    val type = enumeration("type", FileType::class.java)
    val size = integer("size")
    val dateCreated = long("dateCreated")
    val dateUpdated = long("dateUpdated")
}

data class File(
        val id: String,
        val entryId: String,
        val name: String,
        val extension: String,
        val path: String,
        val type: FileType,
        val size: Int,
        val dateCreated: Long,
        val dateUpdated: Long
)

enum class FileType {
    UPLOAD, SCREENSHOT, THUMBNAIL, DOCUMENT
}

// files/{entryId}/{id}