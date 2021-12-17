package lynks.util

import io.ktor.application.*
import lynks.common.page.PageRequest
import lynks.common.page.SortDirection
import org.jetbrains.exposed.sql.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path

fun Query.combine(block: SqlExpressionBuilder.() -> Op<Boolean>): Query {
    return adjustWhere { this?.and(SqlExpressionBuilder.block()) ?: SqlExpressionBuilder.block() }
}

fun ApplicationCall.pageRequest(): PageRequest {
    val page: Long = request.queryParameters["page"]?.toLong() ?: 1
    val size: Int = request.queryParameters["size"]?.toInt() ?: 25
    val tag: String? = request.queryParameters["tag"]
    return PageRequest(page, size, tag)
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
