package lynks.entry.ref

import lynks.common.Entries
import lynks.common.EntryId
import lynks.common.UserId
import lynks.util.loggerFor
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class EntryRefService {

    private val log = loggerFor<EntryRefService>()

    fun getRefsForEntry(userId: UserId, eid: EntryId): EntryRefSet = transaction {
        val inbound =
            EntryRefs.join(Entries, JoinType.INNER, EntryRefs.sourceEntryId, Entries.id)
                .select(EntryRefs.sourceEntryId, Entries.type, Entries.title)
                .where { (EntryRefs.targetEntryId eq eid.value) and (Entries.userId eq userId.value) }
                .map {
                    EntryRefItem(EntryId(it[EntryRefs.sourceEntryId]), it[Entries.type], it[Entries.title])
                }
                .toList()
        val outbound =
            EntryRefs.join(Entries, JoinType.INNER, EntryRefs.targetEntryId, Entries.id)
                .select(EntryRefs.targetEntryId, Entries.type, Entries.title)
                .where { (EntryRefs.sourceEntryId eq eid.value) and (Entries.userId eq userId.value) }
                .map {
                    EntryRefItem(EntryId(it[EntryRefs.targetEntryId]), it[Entries.type], it[Entries.title])
                }
                .toList()
        EntryRefSet(inbound, outbound)
    }

    // refs must already be limited to entries owned by the source entry's user
    fun setEntryRefs(eid: EntryId, refs: List<String>, origin: String) = transaction {
        EntryRefs.deleteWhere { (EntryRefs.sourceEntryId eq eid.value) and (EntryRefs.originId eq origin) }
        EntryRefs.batchInsert(refs) {
            this[EntryRefs.sourceEntryId] = eid.value
            this[EntryRefs.targetEntryId] = it
            this[EntryRefs.originId] = origin
        }
        log.info("Entry references updated for eid={} origin={} count={}", eid, origin, refs.size)
    }

    fun deleteOrigin(originId: String): Int = transaction {
        EntryRefs.deleteWhere { EntryRefs.originId eq originId }
    }

}
