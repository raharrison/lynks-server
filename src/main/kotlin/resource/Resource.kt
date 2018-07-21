package resource

import common.Entries
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object Resources : Table("Resource") {
    val id = varchar("id", 12).primaryKey()
    val entryId = (varchar("entryId", 12).references(Entries.id, ReferenceOption.CASCADE)).index()
    val fileName = varchar("filename", 255)
    val extension = varchar("extension", 4)
    val type = enumeration("type", ResourceType::class.java)
    val size = long("size")
    val dateCreated = long("dateCreated")
    val dateUpdated = long("dateUpdated")
}

data class Resource(
        val id: String,
        val entryId: String,
        val name: String,
        val extension: String,
        val type: ResourceType,
        val size: Long,
        val dateCreated: Long,
        val dateUpdated: Long
)

enum class ResourceType {
    UPLOAD, SCREENSHOT, THUMBNAIL, DOCUMENT
}

// files/{entryId}/{id}