package common

import group.Collection
import group.Tag

data class Link(
    override val id: String,
    override val title: String,
    val url: String,
    val source: String,
    var content: String?,
    override val dateCreated: Long,
    override val dateUpdated: Long,
    override val tags: List<Tag> = emptyList(),
    override val collections: List<Collection> = emptyList(),
    override val props: BaseProperties = BaseProperties(),
    override val version: Int = 0,
    override val starred: Boolean = false,
    override var thumbnailId: String? = null
) : Entry {
    @JvmField
    val type = EntryType.LINK
}


data class NewLink(
    override val id: String? = null,
    val title: String,
    val url: String,
    override val tags: List<String> = emptyList(),
    override val collections: List<String> = emptyList(),
    val process: Boolean = true
) : NewEntry


data class SlimLink(
    override val id: String,
    override val title: String,
    val source: String,
    override val dateUpdated: Long,
    override val tags: List<Tag> = emptyList(),
    override val collections: List<Collection> = emptyList(),
    override val starred: Boolean = false,
    override val thumbnailId: String? = null
) : SlimEntry {
    @JvmField
    val type = EntryType.LINK
}
