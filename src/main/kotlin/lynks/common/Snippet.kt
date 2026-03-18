package lynks.common

import lynks.group.Collection
import lynks.group.Tag
import java.time.Instant

data class Snippet(
    override val id: EntryId,
    val plainContent: String,
    val renderedContent: String,
    override val dateCreated: Instant,
    override val dateUpdated: Instant,
    override val tags: List<Tag> = emptyList(),
    override val collections: List<Collection> = emptyList(),
    override val props: BaseProperties = BaseProperties(),
    override val version: Int = 0,
    override val starred: Boolean = false
) : Entry {
    override val type = EntryType.SNIPPET
}


data class NewSnippet(
    override val id: EntryId? = null,
    val content: String,
    override val tags: List<String> = emptyList(),
    override val collections: List<String> = emptyList()
) : NewEntry


data class SlimSnippet(
    override val id: EntryId,
    val renderedContent: String,
    override val dateUpdated: Instant,
    override val tags: List<Tag> = emptyList(),
    override val collections: List<Collection> = emptyList(),
    override val starred: Boolean = false
) : SlimEntry {
    override val type = EntryType.SNIPPET
}
