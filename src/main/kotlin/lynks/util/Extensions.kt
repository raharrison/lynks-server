package lynks.util

import io.ktor.server.application.*
import io.ktor.server.auth.*
import lynks.common.UserId
import lynks.common.exception.InvalidModelException
import lynks.common.exception.UnauthorizedException
import lynks.common.page.PageRequest
import lynks.common.page.SortDirection
import lynks.user.UserPrincipal
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.Query
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path

fun Query.combine(block: () -> Op<Boolean>): Query {
    return adjustWhere { this?.and(block()) ?: block() }
}

const val MAX_PAGE_SIZE = 100

fun ApplicationCall.pageRequest(): PageRequest {
    val page: Long = request.queryParameters["page"]?.let {
        it.toLongOrNull()?.takeIf { page -> page >= 1 } ?: throw InvalidModelException("Invalid page: $it")
    } ?: 1
    val size: Int = request.queryParameters["size"]?.let {
        it.toIntOrNull()?.takeIf { size -> size >= 1 }?.coerceAtMost(MAX_PAGE_SIZE)
            ?: throw InvalidModelException("Invalid size: $it")
    } ?: 25
    val tags: List<String> = request.queryParameters["tags"]?.split(",") ?: emptyList()
    val collections: List<String> = request.queryParameters["collections"]?.split(",") ?: emptyList()
    val source: String? = request.queryParameters["source"]
    val sort: String? = request.queryParameters["sort"]
    val direction: SortDirection? = request.queryParameters["direction"]?.let {
        SortDirection.entries.find { direction -> direction.name.equals(it, ignoreCase = true) }
            ?: throw InvalidModelException("Invalid direction: $it")
    }
    return PageRequest(page, size, tags, collections, source, sort, direction)
}

fun ApplicationCall.versionParameter(): Int {
    val version = parameters["version"] ?: throw InvalidModelException("Missing version")
    return version.toIntOrNull() ?: throw InvalidModelException("Invalid version: $version")
}

fun ApplicationCall.userId(): UserId = principal<UserPrincipal>()?.id ?: throw UnauthorizedException()

fun Path.toUrlString(): String {
    return toString().replace("\\", "/")
}

fun Table.findColumn(name: String?): Column<*>? {
    if (name == null) return null
    val columnFormat = Normalize.convertToDbColumnName(name)
    return this.columns.find { it.name.equals(name, true) || it.name.equals(columnFormat, true) }
}

fun Query.orderBy(column: Expression<*>, direction: SortDirection): Query = orderBy(listOf(column to direction))

fun Query.orderBy(orders: List<Pair<Expression<*>, SortDirection>>): Query {
    val mappedOrders = orders.map { (column, direction) ->
        if (direction == SortDirection.RAND) Random() to SortOrder.ASC
        else column to SortOrder.valueOf(direction.name)
    }
    return orderBy(*mappedOrders.toTypedArray())
}

inline fun <reified T> loggerFor(): Logger = LoggerFactory.getLogger(T::class.java)
