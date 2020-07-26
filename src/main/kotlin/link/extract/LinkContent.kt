package link.extract

data class LinkContent(
    val title: String,
    val rawContent: String? = null,
    val extractedContent: String? = null,
    val imageUrl: String? = null,
    val keywords: Set<String> = emptySet()
)
