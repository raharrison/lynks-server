package lynks.resource

import lynks.common.*
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.Instant
import java.util.*

object Resources : Table("resources") {
    val id = varchar("id", UID_LENGTH)
    val entryId = (varchar("entry_id", UID_LENGTH).references(Entries.id, ReferenceOption.CASCADE)).index()
    val currentVersion = integer("current_version")
    val fileName = varchar("filename", 255)
    val extension = varchar("extension", 24)
    val type = enumerationByName<ResourceType>("type", 30)
    val dateCreated = timestampWithTimeZone("date_created")
    val dateUpdated = timestampWithTimeZone("date_updated")
    override val primaryKey = PrimaryKey(id)
}

object ResourceVersions : Table("resource_versions") {
    val id = varchar("id", UID_LENGTH)
    val resourceId = varchar("resource_id", UID_LENGTH).references(Resources.id, ReferenceOption.CASCADE).index()
    val version = integer("resource_version")
    val size = long("size")
    val dateCreated = timestampWithTimeZone("date_created")
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
    val dateCreated: Instant
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
    GENERATED, // task created
    SINGLE_FILE; // self-contained HTML with all assets inlined as data URIs

    companion object {
        fun linkBaseline(): EnumSet<ResourceType> =
            EnumSet.of(SCREENSHOT, THUMBNAIL, PREVIEW, DOCUMENT, READABLE_DOC, READABLE_TEXT, SINGLE_FILE)
    }
}
