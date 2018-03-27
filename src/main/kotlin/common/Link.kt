package common

import tag.Tag

data class Link(
        val id: String,
        val title: String,
        val url: String,
        val source: String,
        val dateUpdated: Long,
        val tags: List<Tag>
) : Entry {
    @JvmField
    val type = EntryType.LINK
}


data class NewLink(
        var id: String? = null,
        val title: String,
        val url: String,
        val tags: List<String>
) : NewEntry {
    override fun id(): String? = id
    override fun tags(): List<String> = tags
}
