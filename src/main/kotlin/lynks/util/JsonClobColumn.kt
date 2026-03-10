package lynks.util

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import java.sql.Clob

class JsonClobColumnType<T : Any>(
    private val serialize: (T) -> String,
    private val deserialize: (String) -> T
) : ColumnType<T>() {

    override fun sqlType(): String = "TEXT"

    override fun valueFromDB(value: Any): T = deserialize(
        when (value) {
            is Clob -> value.characterStream.readText()
            is String -> value
            is ByteArray -> value.decodeToString()
            else -> value.toString()
        }
    )

    override fun notNullValueToDB(value: T): Any = serialize(value)

    override fun nonNullValueToString(value: T): String = "'${serialize(value).replace("'", "''")}'"
}

fun <T : Any> Table.jsonClob(
    name: String,
    serialize: (T) -> String,
    deserialize: (String) -> T
): Column<T> = registerColumn(name, JsonClobColumnType(serialize, deserialize))
