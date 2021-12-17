package lynks.suggest

import lynks.group.Collection
import lynks.group.Tag

data class Suggestion(
    val url: String,
    val title: String? = null,
    val thumbnail: String? = null,
    val preview: String? = null,
    val keywords: Set<String> = emptySet(),
    val tags: List<Tag> = emptyList(),
    val collections: List<Collection> = emptyList()
)
