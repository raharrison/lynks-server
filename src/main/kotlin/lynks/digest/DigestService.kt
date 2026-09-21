package lynks.digest

import lynks.common.DigestId
import lynks.common.EntryId
import lynks.common.newDigestId
import lynks.entry.LinkService
import lynks.util.loggerFor
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class DigestService(private val linkService: LinkService) {

    private val log = loggerFor<DigestService>()

    fun generate(): Digest? = transaction {
        val unread = linkService.getUnread()
        if (unread.isEmpty()) {
            log.info("No unread links found, skipping digest generation")
            return@transaction null
        }
        val selected = unread.shuffled().take(DIGEST_SIZE).map { it.id }
        val id = newDigestId()
        Digests.insert {
            it[digestId] = id.value
            it[entryIds] = selected.joinToString(",") { entry -> entry.value }
            it[dateCreated] = OffsetDateTime.now(ZoneOffset.UTC)
        }
        log.info("Generated digest id={} with {} links", id, selected.size)
        get(id)
    }

    fun getLatest(): Digest? = transaction {
        Digests.selectAll()
            .orderBy(Digests.dateCreated to SortOrder.DESC)
            .limit(1)
            .map { toModel(it[Digests.digestId], it[Digests.entryIds], it[Digests.dateCreated].toInstant()) }
            .singleOrNull()
    }

    fun get(id: DigestId): Digest? = transaction {
        Digests.selectAll()
            .where { Digests.digestId eq id.value }
            .map { toModel(it[Digests.digestId], it[Digests.entryIds], it[Digests.dateCreated].toInstant()) }
            .singleOrNull()
    }

    private fun toModel(id: String, entryIds: String, dateCreated: Instant): Digest {
        val ids = entryIds.split(",").filter { it.isNotBlank() }.map { EntryId(it) }
        // resolve on read so a digest reflects current titles and drops deleted entries
        return Digest(DigestId(id), linkService.getSlim(ids), dateCreated)
    }

    companion object {
        private const val DIGEST_SIZE = 5
    }
}
