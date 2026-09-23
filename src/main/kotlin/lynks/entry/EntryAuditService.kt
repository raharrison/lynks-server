package lynks.entry

import lynks.common.*
import lynks.util.RandomUtils
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

class EntryAuditService {

    fun getEntryAudit(userId: UserId, entryId: EntryId): List<EntryAuditItem> = transaction {
        EntryAudit.innerJoin(Entries).select(EntryAudit.columns)
            .where { (EntryAudit.entryId eq entryId.value) and (Entries.userId eq userId.value) }
            .orderBy(EntryAudit.timestamp)
            .map {
                EntryAuditItem(
                    it[EntryAudit.auditId],
                    EntryId(it[EntryAudit.entryId]),
                    it[EntryAudit.src],
                    it[EntryAudit.details],
                    it[EntryAudit.timestamp].toInstant()
                )
            }
    }

    fun acceptAuditEvent(entryId: EntryId, src: String?, details: String): Unit = transaction {
        val id = RandomUtils.generateUid()
        val time = OffsetDateTime.now(ZoneOffset.UTC)
        EntryAudit.insert {
            it[auditId] = id
            it[EntryAudit.entryId] = entryId.value
            it[EntryAudit.src] = src
            it[EntryAudit.details] = details
            it[timestamp] = time
        }
    }

}
