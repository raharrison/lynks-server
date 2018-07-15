package common

import tag.Tag

data class Link(
        override val id: String,
        val title: String,
        val url: String,
        val source: String,
        var content: String?,
        val dateUpdated: Long,
        val tags: List<Tag>,
        override val props: BaseProperties,
        val version: Int=0
) : Entry {
    @JvmField
    val type = EntryType.LINK
}


data class NewLink(
        override val id: String? = null,
        val title: String,
        val url: String,
        override val tags: List<String>,
        val process: Boolean = true
) : NewEntry
