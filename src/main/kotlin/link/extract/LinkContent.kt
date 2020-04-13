package link.extract

data class LinkContent(
    val title: String,
    val content: String? = null,
    val imageUrl: String? = null,
    val keywords: Set<String> = emptySet()
)