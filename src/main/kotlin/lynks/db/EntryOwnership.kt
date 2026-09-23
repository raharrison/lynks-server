package lynks.db

import lynks.common.Entries
import lynks.common.EntryId
import lynks.common.UserId
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

// Comments, resources, reminders, refs and audits are owned through their entry rather than a column of their own
object EntryOwnership {

    fun isOwner(userId: UserId, entryId: EntryId): Boolean = transaction {
        !Entries.select(Entries.id)
            .where { (Entries.id eq entryId.value) and (Entries.userId eq userId.value) }
            .empty()
    }
}
