package lynks.digest

import lynks.common.DigestId
import lynks.common.SlimLink
import lynks.common.TypedIdEntity
import lynks.common.UID_LENGTH
import lynks.user.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.Instant

object Digests : Table("digests") {
    val digestId = varchar("id", UID_LENGTH)
    val userId = varchar("user_id", UID_LENGTH).references(Users.id, ReferenceOption.CASCADE)

    // entry ids rather than a join table: a digest is a frozen snapshot of a random
    // selection, and entries that are later deleted should simply drop out of it
    val entryIds = text("entry_ids")
    val dateCreated = timestampWithTimeZone("date_created")
    override val primaryKey = PrimaryKey(digestId)

    init {
        index(false, userId, dateCreated)
    }
}

data class Digest(
    override val id: DigestId,
    val links: List<SlimLink>,
    val dateCreated: Instant
) : TypedIdEntity<DigestId>
