package common

data class PageRequest(val offset: Long = 0, val limit: Int = 25, val tag: String? = null, val collection: String? = null)

val DefaultPageRequest = PageRequest()