package common

import tag.Tag

data class Link(
        override val id: String,
        val title: String,
        val url: String,
        val source: String,
        val dateUpdated: Long,
        val tags: List<Tag>,
        override val props: BaseProperties
) : Entry {
    @JvmField
    val type = EntryType.LINK
}


data class NewLink(
        override val id: String? = null,
        val title: String,
        val url: String,
        override val tags: List<String>
) : NewEntry
