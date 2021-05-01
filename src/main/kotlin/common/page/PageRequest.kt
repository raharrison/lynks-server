package common.page

enum class SortDirection {
    ASC, DESC;
}

data class PageRequest(
    val page: Long = 1,
    val size: Int = 25,
    val tag: String? = null,
    val collection: String? = null,
    val sort: String? = null,
    val direction: SortDirection? = null
)

val DefaultPageRequest = PageRequest()
