package lynks.common

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.Instant

object EntryAudit : Table("entry_audits") {
    val auditId = varchar("audit_id", UID_LENGTH)
    val entryId = (varchar("entry_id", UID_LENGTH).references(Entries.id, ReferenceOption.CASCADE)).index()
    val src = varchar("source", 255).nullable()
    val details = varchar("details", 255)
    val timestamp = timestampWithTimeZone("timestamp")
    override val primaryKey = PrimaryKey(auditId)
}

data class EntryAuditItem(
    val auditId: String,
    val entryId: EntryId,
    val src: String?,
    val details: String,
    val timestamp: Instant
)
