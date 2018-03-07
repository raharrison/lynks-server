package util

import io.ktor.application.ApplicationCall
import model.PageRequest

fun ApplicationCall.pageRequest(): PageRequest {
    val offset: Int = request.queryParameters["offset"]?.toInt() ?: 0
    val limit: Int = request.queryParameters["limit"]?.toInt() ?: 25
    val tag: String? = request.queryParameters["tag"]
    return PageRequest(offset, limit, tag)
}
