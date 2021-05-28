package link

data class LinkDetails(
    val url: String,
    val title: String,
    val keywords: Set<String> = emptySet(),
    val description: String? = null,
    val image: String? = null,
    val author: String? = null,
    val published: String? = null
)
