package util

import common.PageRequest
import io.ktor.application.ApplicationCall
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.and
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path

fun Query.combine(block: SqlExpressionBuilder.() -> Op<Boolean>): Query {
    return adjustWhere { this?.and(SqlExpressionBuilder.block()) ?: SqlExpressionBuilder.block() }
}

fun ApplicationCall.pageRequest(): PageRequest {
    val offset: Long = request.queryParameters["offset"]?.toLong() ?: 0
    val limit: Int = request.queryParameters["limit"]?.toInt() ?: 25
    val tag: String? = request.queryParameters["tag"]
    return PageRequest(offset, limit, tag)
}

fun Path.toUrlString(): String {
    return toString().replace("\\", "/")
}

inline fun <reified T> loggerFor(): Logger = LoggerFactory.getLogger(T::class.java)