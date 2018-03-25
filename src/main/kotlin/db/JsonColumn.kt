package db

import com.fasterxml.jackson.databind.ObjectMapper
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Table
import util.JsonMapper

fun <T : Any> Table.json(name: String, klass: Class<T>, jsonMapper: ObjectMapper = JsonMapper.defaultMapper): Column<T> = registerColumn(name, Json(klass, jsonMapper))

private class Json<out T : Any>(private val klass: Class<T>, private val jsonMapper: ObjectMapper) : ColumnType() {
    override fun sqlType() = "TEXT"

    override fun valueFromDB(value: Any): Any {
        if (value is String) {
            return try {
                jsonMapper.readValue(value, klass)
            } catch (e: Exception) {
                throw RuntimeException("Can't parse JSON: $value")
            }
        }
        error("Unknown type for blob column :${value.javaClass}")
    }


    override fun notNullValueToDB(value: Any): Any = jsonMapper.writeValueAsString(value)
    override fun nonNullValueToString(value: Any): String = "'${jsonMapper.writeValueAsString(value)}'"
}
