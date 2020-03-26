package common

enum class SortDirection {
    ASC, DESC;
}

data class PageRequest(
    val offset: Long = 0,
    val limit: Int = 25,
    val tag: String? = null,
    val collection: String? = null,
    val sort: String? = null,
    val direction: SortDirection? = null
)

val DefaultPageRequest = PageRequest()