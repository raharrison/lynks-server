package lynks.common

import lynks.group.Collection
import lynks.group.Tag
import java.time.Instant

interface SlimEntry : TypedIdEntity<EntryId> {
    val type: EntryType
    val dateUpdated: Instant
    val starred: Boolean
    val tags: List<Tag>
    val collections: List<Collection>
}
