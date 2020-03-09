package db

import com.fasterxml.jackson.databind.ObjectMapper
import org.jetbrains.exposed.sql.*
import util.JsonMapper
import java.sql.NClob

fun <T : Any> Table.json(name: String, klass: Class<T>, jsonMapper: ObjectMapper = JsonMapper.defaultMapper): Column<T> = registerColumn(name, Json(klass, jsonMapper))

private class Json<out T : Any>(private val klass: Class<T>, private val jsonMapper: ObjectMapper) : ColumnType() {
    override fun sqlType() = "TEXT"

    override fun valueFromDB(value: Any): Any {
        val str = when (value) {
            is String -> value
            is NClob -> value.asciiStream.bufferedReader().use { it.readText() }
            else -> error("Unknown type for blob column :${value.javaClass}")
        }
        return try {
            jsonMapper.readValue(str, klass)
        } catch (e: Exception) {
            throw RuntimeException("Can't parse JSON: $str", e)
        }
    }

    override fun notNullValueToDB(value: Any): Any = when(value) {
        is String -> value
        else -> jsonMapper.writeValueAsString(value)
    }
}

infix fun<T: Any?> ExpressionWithColumnType<T>.notLike(pattern: String): Op<Boolean> = NotLikeOp(this, QueryParameter(pattern, columnType))
