package common.page

data class Page<T>(val content: List<T>, val page: Long, val size: Int, val total: Long) {

    companion object {
        fun <T> of(elements: List<T>, request: PageRequest, total: Long): Page<T> {
            return Page(elements, request.page, request.size, total)
        }

        fun <T> empty(): Page<T> {
            return Page(emptyList(), 1L, 0, 0)
        }
    }

}
