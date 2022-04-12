package lynks.common

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object EntryAudit : Table("ENTRY_AUDIT") {
    val auditId = varchar("AUDIT_ID", 14)
    val entryId = (varchar("ENTRY_ID", 14).references(Entries.id, ReferenceOption.CASCADE)).index()
    val src = varchar("SOURCE", 255).nullable()
    val details = varchar("DETAILS", 255)
    val timestamp = long("TIMESTAMP")
    override val primaryKey = PrimaryKey(auditId)
}

data class EntryAuditItem(
    val auditId: String,
    val entryId: String,
    val src: String?,
    val details: String,
    val timestamp: Long
)
