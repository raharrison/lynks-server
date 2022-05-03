package lynks.common

import com.fasterxml.jackson.annotation.JsonIgnore
import lynks.group.Collection
import lynks.group.Tag

data class Link(
    override val id: String,
    val title: String,
    val url: String,
    val source: String,
    @JsonIgnore // for search so doesn't need to be exposed
    var content: String?,
    override val dateCreated: Long,
    override val dateUpdated: Long,
    override val tags: List<Tag> = emptyList(),
    override val collections: List<Collection> = emptyList(),
    override val props: BaseProperties = BaseProperties(),
    override val version: Int = 0,
    override val starred: Boolean = false,
    var thumbnailId: String? = null,
    val read: Boolean = false
) : Entry {
    override val type = EntryType.LINK
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
    val title: String,
    val source: String,
    override val dateUpdated: Long,
    override val tags: List<Tag> = emptyList(),
    override val collections: List<Collection> = emptyList(),
    override val starred: Boolean = false,
    val thumbnailId: String? = null,
    val read: Boolean = false
) : SlimEntry {
    override val type = EntryType.LINK
}
