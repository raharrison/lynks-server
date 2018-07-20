package common

import tag.Tag

data class Link(
        override val id: String,
        val title: String,
        val url: String,
        val source: String,
        var content: String?,
        override val dateUpdated: Long,
        val tags: List<Tag>,
        override val props: BaseProperties,
        override val version: Int=0,
        override val starred: Boolean=false
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
