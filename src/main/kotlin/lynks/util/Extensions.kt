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
    val tags: List<String> = request.queryParameters["tags"]?.split(",") ?: emptyList()
    val collections: List<String> = request.queryParameters["collections"]?.split(",") ?: emptyList()
    val source: String? = request.queryParameters["source"]
    val sort: String? = request.queryParameters["sort"]
    val direction: SortDirection? = request.queryParameters["direction"]?.let {
        SortDirection.valueOf(it.uppercase())
    }
    return PageRequest(page, size, tags, collections, source, sort, direction)
}

fun Path.toUrlString(): String {
    return toString().replace("\\", "/")
}

fun Table.findColumn(name: String?): Column<*>? {
    if (name == null) return null
    return this.columns.find { it.name.equals(name, true) }
}

fun Query.orderBy(column: Expression<*>, direction: SortDirection): Query {
    val order = SortOrder.valueOf(direction.name)
    return orderBy(column to order)
}

fun Query.orderBy(orders: List<Pair<Expression<*>, SortDirection>>): Query {
    val mappedOrders = orders.map { it.first to SortOrder.valueOf(it.second.name) }
    return orderBy(*mappedOrders.toTypedArray())
}

inline fun <reified T> loggerFor(): Logger = LoggerFactory.getLogger(T::class.java)
