package db

import common.EntryVersions
import org.h2.tools.TriggerAdapter
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.statements.InsertStatement
import java.io.Reader
import java.sql.Connection
import java.sql.ResultSet

class EntryVersionTrigger: TriggerAdapter() {

    private val versionColumnIndex = findVersionColumn()

    private fun findVersionColumn(): Int {
        return EntryVersions.columns.indexOfFirst {
            it == EntryVersions.version
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun fire(conn: Connection?, oldRow: ResultSet?, newRow: ResultSet) {
        if(!newRow.next()) return

        // update operation
        if(oldRow != null) {
            // if no version change
            if(oldRow.getInt(versionColumnIndex + 1) == newRow.getInt(versionColumnIndex + 1))
                return
        }

        val insert : EntryVersions.(InsertStatement<Number>)->Unit = {
            val statement = it
            this.columns.forEachIndexed { index, column ->
                val raw = newRow.getObject(index + 1)
                statement[column as Column<Any?>] = when(raw) {
                    is Reader -> raw.readText()
                    else -> raw
                }
            }
        }
        EntryVersions.insert(insert)
    }

}