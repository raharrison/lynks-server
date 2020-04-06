package suggest

import group.Collection
import group.Tag

data class Suggestion(
    val url: String,
    val title: String? = null,
    val thumbnail: String? = null,
    val screenshot: String? = null,
    val tags: List<Tag> = emptyList(),
    val collections: List<Collection> = emptyList()
)