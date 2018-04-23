package common

import tag.Tag

data class Note(
        override val id: String,
        val title: String,
        val plainText: String,
        val markdownText: String,
        val dateUpdated: Long,
        val tags: List<Tag>,
        override val props: BaseProperties
) : Entry {
    @JvmField
    val type = EntryType.NOTE
}


data class NewNote(
        override val id: String? = null,
        val title: String,
        val plainText: String,
        override val tags: List<String>
) : NewEntry
