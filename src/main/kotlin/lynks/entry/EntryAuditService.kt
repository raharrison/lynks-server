package lynks.entry

import lynks.common.EntryAudit
import lynks.common.EntryAuditItem
import lynks.util.RandomUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

class EntryAuditService {

    fun getEntryAudit(entryId: String): List<EntryAuditItem> = transaction {
        EntryAudit.select { EntryAudit.entryId eq entryId }
            .orderBy(EntryAudit.timestamp)
            .map {
                EntryAuditItem(
                    it[EntryAudit.auditId],
                    it[EntryAudit.entryId],
                    it[EntryAudit.src],
                    it[EntryAudit.details],
                    it[EntryAudit.timestamp]
                )
            }
    }

    fun acceptAuditEvent(entryId: String, src: String?, details: String): Unit = transaction {
        val id = RandomUtils.generateUid()
        val time = System.currentTimeMillis()
        EntryAudit.insert {
            it[auditId] = id
            it[EntryAudit.entryId] = entryId
            it[EntryAudit.src] = src
            it[EntryAudit.details] = details
            it[timestamp] = time
        }
    }

}
