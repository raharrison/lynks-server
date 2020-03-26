package util

import common.PageRequest
import common.SortDirection
import io.ktor.application.ApplicationCall
import org.jetbrains.exposed.sql.*
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

fun Table.findColumn(name: String?): Column<*>? {
    if (name == null) return null
    return this.columns.find { it.name == name }
}

fun Query.orderBy(column: Expression<*>, direction: SortDirection): Query {
    val order = SortOrder.valueOf(direction.name)
    return orderBy(column to order)
}

inline fun <reified T> loggerFor(): Logger = LoggerFactory.getLogger(T::class.java)