package lynks.db

import lynks.common.EntryVersions
import org.h2.tools.TriggerAdapter
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.statements.InsertStatement
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

        val insert: EntryVersions.(InsertStatement<Number>) -> Unit = {
            val statement = it
            this.columns.forEach { column ->
                val raw = newRow.getObject(column.name)
                statement[column as Column<Any?>] = when (raw) {
                    is Reader -> raw.readText()
                    else -> raw
                }
            }
        }
        EntryVersions.insert(insert)
    }

}
