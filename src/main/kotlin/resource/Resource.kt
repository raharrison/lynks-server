package resource

import common.Entries
import common.IdBasedCreatedEntity
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import java.util.*

object Resources : Table("RESOURCE") {
    val id = varchar("ID", 12)
    val entryId = (varchar("ENTRY_ID", 12).references(Entries.id, ReferenceOption.CASCADE)).index()
    val fileName = varchar("FILENAME", 255)
    val extension = varchar("EXTENSION", 4)
    val type = enumeration("TYPE", ResourceType::class)
    val size = long("SIZE")
    val dateCreated = long("DATE_CREATED")
    val dateUpdated = long("DATE_UPDATED")
    override val primaryKey = PrimaryKey(id)
}

data class Resource(
        override val id: String,
        val entryId: String,
        val name: String,
        val extension: String,
        val type: ResourceType,
        val size: Long,
        val dateCreated: Long,
        val dateUpdated: Long
): IdBasedCreatedEntity

enum class ResourceType {
    UPLOAD, SCREENSHOT, THUMBNAIL, DOCUMENT, GENERATED;

    companion object {
        fun all() = EnumSet.allOf(ResourceType::class.java)
    }
}

// files/{entryId}/{id}