package lynks.resource

import lynks.common.*
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import java.util.*

object Resources : Table("RESOURCE") {
    val id = varchar("ID", UID_LENGTH)
    val entryId = (varchar("ENTRY_ID", UID_LENGTH).references(Entries.id, ReferenceOption.CASCADE)).index()
    val currentVersion = integer("CURRENT_VERSION")
    val fileName = varchar("FILENAME", 255)
    val extension = varchar("EXTENSION", 4)
    val type = enumeration<ResourceType>("TYPE")
    val dateCreated = long("DATE_CREATED")
    val dateUpdated = long("DATE_UPDATED")
    override val primaryKey = PrimaryKey(id)
}

object ResourceVersions : Table("RESOURCE_VERSIONS") {
    val id = varchar("ID", UID_LENGTH)
    val resourceId = varchar("RESOURCE_ID", UID_LENGTH).references(Resources.id, ReferenceOption.CASCADE).index()
    val version = integer("RESOURCE_VERSION")
    val size = long("SIZE")
    val dateCreated = long("DATE_CREATED")
    override val primaryKey = PrimaryKey(id)
}

data class Resource(
    override val id: ResourceId,
    val parentId: String,
    val entryId: EntryId,
    val version: Int,
    val name: String,
    val extension: String,
    val type: ResourceType,
    val size: Long,
    val dateCreated: Long
) : TypedIdEntity<ResourceId>

enum class ResourceType {
    UPLOAD, // user uploaded
    SCREENSHOT, // full page image screenshot
    THUMBNAIL, // primary image from page or small screenshot
    PREVIEW, // small partial page screenshot
    PAGE, // full HTML page
    DOCUMENT, // full page PDF
    READABLE_DOC, // extracted formatted readable content
    READABLE_TEXT, // extracted text content only
    GENERATED; // task created

    companion object {
        fun linkBaseline(): EnumSet<ResourceType> = EnumSet.of(SCREENSHOT, THUMBNAIL, PREVIEW, PAGE, DOCUMENT, READABLE_DOC, READABLE_TEXT)
    }
}
