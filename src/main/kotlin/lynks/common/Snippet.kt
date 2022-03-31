package lynks.common

import lynks.group.Collection
import lynks.group.Tag

data class Snippet(
    override val id: String,
    val plainText: String,
    val markdownText: String,
    override val dateCreated: Long,
    override val dateUpdated: Long,
    override val tags: List<Tag> = emptyList(),
    override val collections: List<Collection> = emptyList(),
    override val props: BaseProperties = BaseProperties(),
    override val version: Int = 0,
    override val starred: Boolean = false
) : Entry {
    @JvmField
    val type = EntryType.SNIPPET
}


data class NewSnippet(
    override val id: String? = null,
    val plainText: String,
    override val tags: List<String> = emptyList(),
    override val collections: List<String> = emptyList()
) : NewEntry


data class SlimSnippet(
    override val id: String,
    val markdownText: String,
    override val dateUpdated: Long,
    override val tags: List<Tag> = emptyList(),
    override val collections: List<Collection> = emptyList(),
    override val starred: Boolean = false
) : SlimEntry {
    @JvmField
    val type = EntryType.SNIPPET
}
