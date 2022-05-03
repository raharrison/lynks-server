package lynks.common

import lynks.group.Collection
import lynks.group.Tag

data class Note(
    override val id: String,
    val title: String,
    val plainText: String,
    val markdownText: String,
    override val dateCreated: Long,
    override val dateUpdated: Long,
    override val tags: List<Tag> = emptyList(),
    override val collections: List<Collection> = emptyList(),
    override val props: BaseProperties = BaseProperties(),
    override val version: Int = 0,
    override val starred: Boolean = false,
) : Entry {
    override val type = EntryType.NOTE
}


data class NewNote(
    override val id: String? = null,
    val title: String,
    val plainText: String,
    override val tags: List<String> = emptyList(),
    override val collections: List<String> = emptyList()
) : NewEntry


data class SlimNote(
    override val id: String,
    val title: String,
    override val dateUpdated: Long,
    override val tags: List<Tag> = emptyList(),
    override val collections: List<Collection> = emptyList(),
    override val starred: Boolean = false
) : SlimEntry {
    override val type = EntryType.NOTE
}
