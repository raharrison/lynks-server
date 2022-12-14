package lynks.entry.ref

import lynks.common.Entries
import lynks.common.EntryType
import lynks.common.UID_LENGTH
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object EntryRefs : Table("ENTRY_REF") {
    val sourceEntryId = (varchar("SOURCE_ENTRY_ID", UID_LENGTH).references(Entries.id, ReferenceOption.CASCADE))
    val targetEntryId = (varchar("TARGET_ENTRY_ID", UID_LENGTH).references(Entries.id, ReferenceOption.CASCADE))
    val originId = varchar("ORIGIN", UID_LENGTH)
    override val primaryKey = PrimaryKey(sourceEntryId, targetEntryId, originId)
}

data class EntryRefItem(val entryId: String, val entryType: EntryType, val title: String? = null)
data class EntryRefSet(val inbound: List<EntryRefItem>, val outbound: List<EntryRefItem>)
