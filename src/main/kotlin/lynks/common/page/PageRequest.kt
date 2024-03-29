package lynks.common.page

enum class SortDirection {
    ASC, DESC, RAND;
}

data class PageRequest(
    val page: Long = 1,
    val size: Int = 25,
    val tags: List<String> = emptyList(),
    val collections: List<String> = emptyList(),
    val source: String? = null,
    val sort: String? = null,
    val direction: SortDirection? = null
)

val DefaultPageRequest = PageRequest()
