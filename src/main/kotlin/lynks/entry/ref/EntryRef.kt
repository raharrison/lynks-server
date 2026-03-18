package lynks.entry.ref

import lynks.common.Entries
import lynks.common.EntryId
import lynks.common.EntryType
import lynks.common.UID_LENGTH
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object EntryRefs : Table("entry_refs") {
    val sourceEntryId = (varchar("source_entry_id", UID_LENGTH).references(Entries.id, ReferenceOption.CASCADE))
    val targetEntryId = (varchar("target_entry_id", UID_LENGTH).references(Entries.id, ReferenceOption.CASCADE)).index()
    val originId = varchar("origin", UID_LENGTH)
    override val primaryKey = PrimaryKey(sourceEntryId, targetEntryId, originId)
}

data class EntryRefItem(val entryId: EntryId, val entryType: EntryType, val title: String? = null)
data class EntryRefSet(val inbound: List<EntryRefItem>, val outbound: List<EntryRefItem>)
