package lynks.db

import lynks.common.EntryVersions
import org.h2.jdbc.JdbcClob
import org.h2.tools.TriggerAdapter
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.EnumerationNameColumnType
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.json.JsonColumnType
import java.io.Reader
import java.sql.Connection
import java.sql.ResultSet

class EntryVersionTrigger : TriggerAdapter() {

    private val versionColumnName = EntryVersions.version.name

    @Suppress("UNCHECKED_CAST")
    override fun fire(conn: Connection?, oldRow: ResultSet?, newRow: ResultSet) {
        if (!newRow.next()) return

        // update operation
        if (oldRow != null) {
            // if no version change
            if (oldRow.getInt(versionColumnName) == newRow.getInt(versionColumnName))
                return
        }

        EntryVersions.insert {
            EntryVersions.columns.forEach { column ->
                newRow.getObject(column.name)?.let { raw ->
                    val value = when {
                        column.columnType is EnumerationNameColumnType<*> ||
                            column.columnType is JsonColumnType<*> ->
                            column.columnType.valueFromDB(raw)

                        raw is Reader -> raw.readText()
                        raw is JdbcClob -> raw.characterStream.readText()
                        else -> raw
                    }
                    it[column as Column<Any?>] = value
                }
            }
        }

    }

}
