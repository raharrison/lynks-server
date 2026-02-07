package lynks.common

import lynks.group.Collection
import lynks.group.Tag

interface SlimEntry : TypedIdEntity<EntryId> {
    val type: EntryType
    val dateUpdated: Long
    val starred: Boolean
    val tags: List<Tag>
    val collections: List<Collection>
}
