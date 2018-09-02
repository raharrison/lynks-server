package common

data class PageRequest(val offset: Int = 0, val limit: Int = 25, val tag: String? = null)

val DefaultPageRequest = PageRequest()