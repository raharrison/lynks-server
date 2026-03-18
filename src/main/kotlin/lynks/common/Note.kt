package lynks.common

import lynks.group.Collection
import lynks.group.Tag
import java.time.Instant

data class Note(
    override val id: EntryId,
    val title: String,
    val plainContent: String,
    val renderedContent: String,
    override val dateCreated: Instant,
    override val dateUpdated: Instant,
    override val tags: List<Tag> = emptyList(),
    override val collections: List<Collection> = emptyList(),
    override val props: BaseProperties = BaseProperties(),
    override val version: Int = 0,
    override val starred: Boolean = false,
) : Entry {
    override val type = EntryType.NOTE
}


data class NewNote(
    override val id: EntryId? = null,
    val title: String,
    val content: String,
    override val tags: List<String> = emptyList(),
    override val collections: List<String> = emptyList()
) : NewEntry


data class SlimNote(
    override val id: EntryId,
    val title: String,
    override val dateUpdated: Instant,
    override val tags: List<Tag> = emptyList(),
    override val collections: List<Collection> = emptyList(),
    override val starred: Boolean = false
) : SlimEntry {
    override val type = EntryType.NOTE
}
