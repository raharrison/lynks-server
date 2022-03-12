package lynks.common.page

enum class SortDirection {
    ASC, DESC;
}

data class PageRequest(
    val page: Long = 1,
    val size: Int = 25,
    val tags: List<String> = emptyList(),
    val collections: List<String> = emptyList(),
    val sort: String? = null,
    val direction: SortDirection? = null
)

val DefaultPageRequest = PageRequest()
