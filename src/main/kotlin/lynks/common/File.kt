package lynks.common

import lynks.group.Collection
import lynks.group.Tag

data class File(
    override val id: String,
    val title: String,
    override val dateCreated: Long,
    override val dateUpdated: Long,
    override val tags: List<Tag> = emptyList(),
    override val collections: List<Collection> = emptyList(),
    override val props: BaseProperties = BaseProperties(),
    override val version: Int = 0,
    override val starred: Boolean = false,
) : Entry {
    @JvmField
    val type = EntryType.FILE
}


data class NewFile(
    override val id: String? = null,
    val title: String,
    override val tags: List<String> = emptyList(),
    override val collections: List<String> = emptyList()
) : NewEntry


data class SlimFile(
    override val id: String,
    val title: String,
    override val dateUpdated: Long,
    override val tags: List<Tag> = emptyList(),
    override val collections: List<Collection> = emptyList(),
    override val starred: Boolean = false
) : SlimEntry {
    @JvmField
    val type = EntryType.FILE
}
