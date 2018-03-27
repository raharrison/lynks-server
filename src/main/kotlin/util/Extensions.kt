package util

import common.PageRequest
import io.ktor.application.ApplicationCall
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.and

fun Query.combine(block: SqlExpressionBuilder.() -> Op<Boolean>): Query {
    return adjustWhere { this?.and(SqlExpressionBuilder.block()) ?: SqlExpressionBuilder.block() }
}

fun ApplicationCall.pageRequest(): PageRequest {
    val offset: Int = request.queryParameters["offset"]?.toInt() ?: 0
    val limit: Int = request.queryParameters["limit"]?.toInt() ?: 25
    val tag: String? = request.queryParameters["tag"]
    return PageRequest(offset, limit, tag)
}
